package com.fitnesslife.gym.service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
import com.fitnesslife.gym.enums.Role;
import com.fitnesslife.gym.exception.ChatException;
import com.fitnesslife.gym.model.ChatConversation;
import com.fitnesslife.gym.model.ChatMessage;
import com.fitnesslife.gym.model.ChatSource;
import com.fitnesslife.gym.model.PendingAction;
import com.fitnesslife.gym.model.User;
import com.fitnesslife.gym.repository.ChatConversationStore;
import com.fitnesslife.gym.service.KnowledgeRetriever.Hit;
import com.fitnesslife.gym.tool.ClassActionTool;
import com.fitnesslife.gym.tool.ClassAvailabilityTool;
import com.fitnesslife.gym.tool.MembershipTool;
import com.fitnesslife.gym.tool.ToolCallRecorder;
import com.fitnesslife.gym.tool.ToolSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    public record Reply(String conversationId, ChatMessage message, boolean grounded,
                        List<PendingAction> pendingActions) {
    }

    private record ParsedAnswer(String text, List<ChatSource> sources) {
    }

    private record Answer(String text, List<ChatSource> sources, boolean realtime, List<PendingAction> pending) {
    }

    private static final Pattern SOURCES_LINE = Pattern.compile("(?im)^[ \\t]*FUENTES:[ \\t]*(.*?)[ \\t]*$");
    private static final Pattern NUMBER = Pattern.compile("\\d{1,3}");
    private static final Pattern GREETING = Pattern.compile(
            "^(hola|holi|hey|saludos|buen[oa]s|buen[oa]s (d[ií]as|tardes|noches)|qu[eé] tal|ayuda"
                    + "|qu[eé] puedes hacer|en qu[eé] me puedes ayudar)[\\s!¡.,?¿]*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern THANKS = Pattern.compile(
            "^((muchas |mil )?gracias|ok gracias|listo gracias|perfecto gracias|adi[oó]s|chao|hasta luego)"
                    + "[\\s!¡.,?¿]*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern CODE_OR_MARKUP = Pattern.compile("```|</?[a-zA-Z][a-zA-Z0-9]*(\\s[^>]*)?>");
    /** The model must never present a prepared request as already done. */
    private static final Pattern DONE_CLAIM = Pattern.compile(
            "(?i)(qued[óo]\\b|quedaste|ya est[aá]s? inscrit|te inscrib(í|i|imos)|inscripci[oó]n (realizada|exitosa|confirmada)"
                    + "|(fue|se|ha sido) cancelad|se cancel[oó]|cancel[eé]\\b|cancelamos|ya (te )?(inscrib|cancel))");
    private static final String PENDING_NOTICE =
            "Aún no se ha hecho ningún cambio: usa los botones de abajo para confirmar o cancelar la solicitud.";
    private static final String NO_ANSWER_MARKER = "SIN_RESPUESTA";
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
    private final ClassAvailabilityTool classAvailabilityTool;
    private final MembershipTool membershipTool;
    private final ClassActionTool classActionTool;
    private final PendingActionService pendingActionService;

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

        Answer answer = answerFor(user, id, history, message);

        Instant now = Instant.now();
        ChatMessage userMessage = ChatMessage.builder()
                .id(new ObjectId().toHexString()).role(ChatRole.USER).content(message).createdAt(now).build();
        ChatMessage assistantMessage = ChatMessage.builder()
                .id(new ObjectId().toHexString()).role(ChatRole.ASSISTANT).content(answer.text())
                .createdAt(now.plusMillis(1)).sources(answer.sources()).realtime(answer.realtime()).build();

        if (isNew) {
            store.insert(ChatConversation.builder()
                    .id(id).userId(user.getId()).title(titleFrom(message))
                    .createdAt(now).updatedAt(now).messageCount(2)
                    .messages(new ArrayList<>(List.of(userMessage, assistantMessage))).build());
        } else if (!store.appendExchange(id, user.getId(), List.of(userMessage, assistantMessage),
                emptyConversation ? titleFrom(message) : null, maxMessages, now)) {
            throw ChatException.notFound();
        }

        log.info("Mensaje procesado en la conversación {}: {} fuentes, datos en tiempo real: {}, {} solicitudes "
                + "pendientes", id, answer.sources().size(), answer.realtime(), answer.pending().size());
        return new Reply(id, assistantMessage, !answer.sources().isEmpty() || answer.realtime(), answer.pending());
    }

    /**
     * Decides what the user gets. Only these reach them: a fixed server message, or a model answer backed by evidence
     * the server verified (a cited retrieved document or a tool that ran successfully). Prepared actions are never
     * presented as done.
     */
    private Answer answerFor(User user, String conversationId, List<ChatMessage> history, String message) {
        String smallTalk = smallTalkReply(message);
        if (smallTalk != null) {
            return new Answer(smallTalk, List.of(), false, List.of());
        }

        List<Hit> hits = retriever.retrieve(retrievalQuery(history, message),
                KnowledgeAudience.visibleTo(user.getRole()));
        boolean accountTools = user.getRole() == Role.USER;
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of(properties.tools().timezone()));
        ChatPromptBuilder.BuiltPrompt prompt = promptBuilder.build(history, hits, message, now, accountTools);

        ToolCallRecorder recorder = new ToolCallRecorder(properties.tools().maxCallsPerMessage());
        ToolSession session = new ToolSession(user.getId(), user.getIdentification(), user.getRole(),
                conversationId, recorder);
        Object[] tools = accountTools
                ? new Object[] { classAvailabilityTool, membershipTool, classActionTool }
                : new Object[] { classAvailabilityTool };

        String raw = callModel(prompt.messages(), tools, session.asContext());
        log.info("{} fragmentos recuperados, {} herramientas ejecutadas con éxito", hits.size(),
                recorder.successfulCalls());

        Answer answer = validate(raw, prompt.usedHits(), recorder);
        List<PendingAction> pending = pendingActionService.findWaitingByIds(user.getId(), recorder.pendingActionIds());
        return pending.isEmpty() ? answer : withPendingWording(answer, pending);
    }

    private Answer validate(String raw, List<Hit> usedHits, ToolCallRecorder recorder) {
        String compact = raw.strip();
        if (compact.length() <= 40 && compact.toUpperCase(Locale.ROOT).contains(NO_ANSWER_MARKER)) {
            log.info("El modelo indicó que no hay información suficiente");
            return refusal();
        }

        ParsedAnswer parsed = parseAnswer(raw, usedHits);
        boolean realtime = recorder.successfulCalls() > 0;
        if (parsed.sources().isEmpty() && !realtime) {
            log.warn("Respuesta descartada: no cita ninguna fuente documental válida ni usó herramientas");
            return refusal();
        }
        if (CODE_OR_MARKUP.matcher(parsed.text()).find()) {
            log.warn("Respuesta descartada: contiene código o marcado");
            return refusal();
        }
        return new Answer(parsed.text(), parsed.sources(), realtime, List.of());
    }

    /** With a request waiting for confirmation, the text must say so: never that it was already done. */
    private Answer withPendingWording(Answer answer, List<PendingAction> pending) {
        String summaries = pending.stream().map(PendingAction::getSummary).collect(Collectors.joining(" "));
        String neutral = "Preparé esta solicitud: " + summaries + " " + PENDING_NOTICE;

        boolean unusable = answer.text().equals(refusal().text()) || DONE_CLAIM.matcher(answer.text()).find();
        String text = unusable ? neutral : answer.text() + "\n\n" + PENDING_NOTICE;
        if (unusable) {
            log.warn("El modelo presentó como hecha una solicitud pendiente: se reemplazó el texto");
        }
        return new Answer(text, answer.sources(), true, pending);
    }

    private Answer refusal() {
        ChatbotProperties.Assistant assistant = properties.assistant();
        return new Answer(assistant.noAnswerMessage().replace("{contact}", assistant.contact()), List.of(), false,
                List.of());
    }

    private String smallTalkReply(String message) {
        if (message.length() > SHORT_MESSAGE_CHARS) {
            return null;
        }
        if (GREETING.matcher(message).matches()) {
            return properties.assistant().greetingMessage();
        }
        if (THANKS.matcher(message).matches()) {
            return properties.assistant().thanksMessage();
        }
        return null;
    }

    private String callModel(List<Message> messages, Object[] tools, Map<String, Object> toolContext) {
        ChatbotProperties.Llm llm = properties.llm();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .temperature(llm.temperature())
                .maxCompletionTokens(llm.maxCompletionTokens())
                .reasoningEffort(StringUtils.hasText(llm.reasoningEffort()) ? llm.reasoningEffort() : null)
                .build();
        long started = System.nanoTime();
        try {
            String content = chatClient.prompt().messages(messages).options(options).tools(tools)
                    .toolContext(toolContext).call().content();
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

    /** Short follow-ups ("¿y cuánto cuesta?") are retrieved together with the previous user question. */
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

    /** Extracts the cited documents. Without a valid citation line there are no sources. */
    private ParsedAnswer parseAnswer(String raw, List<Hit> usedHits) {
        String text = raw;
        Set<Integer> cited = new LinkedHashSet<>();

        Matcher line = SOURCES_LINE.matcher(raw);
        int start = -1;
        int end = -1;
        String value = null;
        while (line.find()) {
            start = line.start();
            end = line.end();
            value = line.group(1);
        }
        if (value != null) {
            text = (raw.substring(0, start) + raw.substring(end)).strip();
            Matcher numbers = NUMBER.matcher(value);
            while (numbers.find()) {
                cited.add(Integer.parseInt(numbers.group()));
            }
        }
        if (text.length() > MAX_ANSWER_CHARS) {
            text = text.substring(0, MAX_ANSWER_CHARS) + "…";
        }

        List<ChatSource> sources = cited.stream()
                .filter(n -> n >= 1 && n <= usedHits.size())
                .map(n -> toSource(usedHits.get(n - 1)))
                .distinct()
                .toList();
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