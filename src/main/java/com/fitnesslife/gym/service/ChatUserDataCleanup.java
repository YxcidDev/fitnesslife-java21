package com.fitnesslife.gym.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.fitnesslife.gym.event.UserDeletedEvent;
import com.fitnesslife.gym.repository.ChatConversationStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatUserDataCleanup {

    private final ChatConversationStore store;

    @EventListener
    public void onUserDeleted(UserDeletedEvent event) {
        long deleted = store.deleteAllByUser(event.userId());
        log.info("Se eliminaron {} conversaciones del usuario eliminado {}", deleted, event.userId());
    }
}