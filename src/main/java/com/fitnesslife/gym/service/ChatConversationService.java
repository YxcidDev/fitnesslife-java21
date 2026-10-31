package com.fitnesslife.gym.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import com.fitnesslife.gym.config.ChatbotProperties;
import com.fitnesslife.gym.exception.ChatException;
import com.fitnesslife.gym.model.ChatConversation;
import com.fitnesslife.gym.model.ChatMessage;
import com.fitnesslife.gym.model.User;
import com.fitnesslife.gym.repository.ChatConversationStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatConversationService {

    private final UserService userService;
    private final ChatConversationStore store;
    private final ChatbotProperties properties;

    public ChatConversation create(String userEmail) {
        User user = userService.getUserOrThrow(userEmail);
        if (store.countOwned(user.getId()) >= properties.conversation().maxConversationsPerUser()) {
            throw ChatException.conversationLimit();
        }
        Instant now = Instant.now();
        ChatConversation conversation = ChatConversation.builder()
                .id(store.newId()).userId(user.getId()).title("Nueva conversación")
                .createdAt(now).updatedAt(now).messageCount(0).messages(new ArrayList<>()).build();
        store.insert(conversation);
        log.info("Conversación {} creada", conversation.getId());
        return conversation;
    }

    public List<ChatConversation> list(String userEmail, int page, int size) {
        User user = userService.getUserOrThrow(userEmail);
        return store.listOwned(user.getId(), page, size);
    }

    public List<ChatMessage> messages(String userEmail, String conversationId, int limit) {
        User user = userService.getUserOrThrow(userEmail);
        ChatConversation conversation = store.findOwned(conversationId, user.getId(), limit)
                .orElseThrow(ChatException::notFound);
        return conversation.getMessages() == null ? List.of() : conversation.getMessages();
    }

    public void delete(String userEmail, String conversationId) {
        User user = userService.getUserOrThrow(userEmail);
        if (!store.delete(conversationId, user.getId())) {
            throw ChatException.notFound();
        }
        log.info("Conversación {} eliminada por su propietario", conversationId);
    }
}