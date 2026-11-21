package com.fitnesslife.gym.service;

import com.fitnesslife.gym.repository.PendingActionRepository;
import com.fitnesslife.gym.repository.ToolAuditRepository;
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
    private final ToolAuditRepository toolAuditRepository;
    private final PendingActionRepository pendingActionRepository;

    @EventListener
    public void onUserDeleted(UserDeletedEvent event) {
        long deleted = store.deleteAllByUser(event.userId());
        log.info("Se eliminaron {} conversaciones del usuario eliminado {}", deleted, event.userId());
        long audits = toolAuditRepository.deleteByUserId(event.userId());
        log.info("Se eliminaron {} registros de auditoría de herramientas del usuario {}", audits, event.userId());
        pendingActionRepository.deleteByUserId(event.userId());
    }
}