package com.fitnesslife.gym.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bson.types.ObjectId;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.fitnesslife.gym.config.ChatbotProperties;
import com.fitnesslife.gym.enums.ChatRole;
import com.fitnesslife.gym.enums.KnowledgeAudience;
import com.fitnesslife.gym.exception.ChatException;
import com.fitnesslife.gym.model.ChatConversation;
import com.fitnesslife.gym.model.ChatMessage;
import com.fitnesslife.gym.model.ChatSource;
import com.fitnesslife.gym.model.User;
import com.fitnesslife.gym.repository.ChatConversationStore;
import com.fitnesslife.gym.service.KnowledgeRetriever.Hit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    public record Reply(String conversationId, ChatMessage message, boolean grounded) {
    }

    private record ParsedAnswer(String text, List<ChatSource> sources) {
    }

    private static final Pattern SOURCES_LINE = Pattern.compile("(?im)^[ \\t]*FUENTES:[ \\t]*(.*?)[ \\t]*$");
    private static final Pattern NUMBER = Pattern.compile("\\d{1,3}");
    private static final int MAX_ANSWER_CHARS = 8000;
    private static final int SHORT_MESSAGE_CHARS = 60;
    private static final Duration LOCK_TTL = Duration.ofSeconds(120);

    private final UserService userService;
    private final ChatConversationStore store;
    private final ChatRateLimiter rateLimiter;
    private final RedisLockService lockService;
    private final KnowledgeRetriever retriever;
    private final ChatPromptBuilder promptBuilder;
    private final ChatClient chatClient;
    private final ChatbotProperties properties;

    public Reply sendMessage(String userEmail, String conversationId, String rawMessage) {
        User user = userService.getUserOrThrow(userEmail);
        String message = sanitize(rawMessage);
        rateLimiter.check(user.getId());

        boolean isNew = !StringUtils.hasText(conversationId);
        if (isNew && store.countOwned(user.getId()) >= properties.conversation().maxConversationsPerUser()) {
            throw ChatException.conversationLimit();
        }
        String id = isNew ? store.newId() : conversationId;

        String lockKey = "chat:lock:" + user.getId() + ":" + id;
        String lockToken = lockService.tryAcquire(lockKey, LOCK_TTL).orElseThrow(ChatException::busy);
        try {
            return process(user, id, isNew, message);
        } finally {
            lockService.release(lockKey, lockToken);
        }
    }

    private Reply process(User user, String id, boolean isNew, String message) {
        int maxMessages = properties.conversation().maxMessagesPerConversation();
        List<ChatMessage> history = List.of();
        boolean emptyConversation = isNew;

        if (!isNew) {
            ChatConversation existing = store
                    .findOwned(id, user.getId(), properties.conversation().historyMessages())
                    .orElseThrow(ChatException::notFound);
            if (existing.getMessageCount() + 2 > maxMessages) {
                throw ChatException.conversationFull();
            }
            history = existing.getMessages() == null ? List.of() : existing.getMessages();
            emptyConversation = existing.getMessageCount() == 0;
        }

        List<Hit> hits = retriever.retrieve(retrievalQuery(history, message),
                KnowledgeAudience.visibleTo(user.getRole()));
        ChatPromptBuilder.BuiltPrompt prompt = promptBuilder.build(history, hits, message);
        ParsedAnswer answer = parseAnswer(callModel(prompt.messages()), prompt.usedHits());

        Instant now = Instant.now();
        ChatMessage userMessage = ChatMessage.builder()
                .id(new ObjectId().toHexString()).role(ChatRole.USER).content(message).createdAt(now).build();
        ChatMessage assistantMessage = ChatMessage.builder()
                .id(new ObjectId().toHexString()).role(ChatRole.ASSISTANT).content(answer.text())
                .createdAt(now.plusMillis(1)).sources(answer.sources()).build();

        if (isNew) {
            store.insert(ChatConversation.builder()
                    .id(id).userId(user.getId()).title(titleFrom(message))
                    .createdAt(now).updatedAt(now).messageCount(2)
                    .messages(new ArrayList<>(List.of(userMessage, assistantMessage))).build());
        } else if (!store.appendExchange(id, user.getId(), List.of(userMessage, assistantMessage),
                emptyConversation ? titleFrom(message) : null, maxMessages, now)) {
            throw ChatException.notFound();
        }

        log.info("Mensaje procesado en la conversación {}: {} fragmentos recuperados, {} fuentes citadas",
                id, hits.size(), answer.sources().size());
        return new Reply(id, assistantMessage, !answer.sources().isEmpty());
    }

    private String callModel(List<Message> messages) {
        ChatbotProperties.Llm llm = properties.llm();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .temperature(llm.temperature())
                .maxCompletionTokens(llm.maxCompletionTokens())
                .reasoningEffort(StringUtils.hasText(llm.reasoningEffort()) ? llm.reasoningEffort() : null)
                .build();
        long started = System.nanoTime();
        try {
            String content = chatClient.prompt().messages(messages).options(options).call().content();
            if (!StringUtils.hasText(content)) {
                log.warn("El modelo devolvió una respuesta vacía");
                throw ChatException.providerFailure();
            }
            log.info("Respuesta del modelo recibida en {} ms", (System.nanoTime() - started) / 1_000_000);
            return content.trim();
        } catch (ChatException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Falló la llamada al modelo de chat ({})", e.getClass().getSimpleName());
            throw e instanceof TransientAiException ? ChatException.providerBusy() : ChatException.providerFailure();
        }
    }

    private String retrievalQuery(List<ChatMessage> history, String message) {
        if (message.length() >= SHORT_MESSAGE_CHARS) {
            return message;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).getRole() == ChatRole.USER) {
                String combined = history.get(i).getContent() + "\n" + message;
                return combined.length() > 1500 ? combined.substring(combined.length() - 1500) : combined;
            }
        }
        return message;
    }

    private ParsedAnswer parseAnswer(String raw, List<Hit> usedHits) {
        String text = raw;
        Set<Integer> cited = new LinkedHashSet<>();
        boolean citationFound = false;

        Matcher line = SOURCES_LINE.matcher(raw);
        int start = -1;
        int end = -1;
        String value = null;
        while (line.find()) {
            start = line.start();
            end = line.end();
            value = line.group(1);
            citationFound = true;
        }
        if (citationFound) {
            text = (raw.substring(0, start) + raw.substring(end)).strip();
            Matcher numbers = NUMBER.matcher(value);
            while (numbers.find()) {
                cited.add(Integer.parseInt(numbers.group()));
            }
        }
        if (text.length() > MAX_ANSWER_CHARS) {
            text = text.substring(0, MAX_ANSWER_CHARS) + "…";
        }

        List<ChatSource> sources = citationFound
                ? cited.stream().filter(n -> n >= 1 && n <= usedHits.size())
                        .map(n -> toSource(usedHits.get(n - 1))).distinct().toList()
                : usedHits.stream().map(this::toSource).distinct().toList();
        return new ParsedAnswer(text, sources);
    }

    private ChatSource toSource(Hit hit) {
        return ChatSource.builder().title(hit.title()).section(hit.section()).build();
    }

    private String sanitize(String raw) {
        return raw.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[\\p{Cntrl}&&[^\n\t]]", "")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }

    private String titleFrom(String message) {
        String single = message.replaceAll("\\s+", " ").strip();
        return single.length() <= 60 ? single : single.substring(0, 57).strip() + "…";
    }
}