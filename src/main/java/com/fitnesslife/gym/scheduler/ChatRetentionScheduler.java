package com.fitnesslife.gym.scheduler;

import java.time.Duration;
import java.time.Instant;

import com.fitnesslife.gym.repository.PendingActionRepository;
import com.fitnesslife.gym.repository.ToolAuditRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.fitnesslife.gym.config.ChatbotProperties;
import com.fitnesslife.gym.repository.ChatConversationStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRetentionScheduler {

    private final ChatConversationStore store;
    private final ChatbotProperties properties;
    private final ToolAuditRepository toolAuditRepository;
    private final PendingActionRepository pendingActionRepository;

    @Scheduled(cron = "0 30 3 * * *")
    public void purgeInactiveConversations() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(properties.conversation().retentionDays()));
        long deleted = store.deleteInactiveSince(cutoff);
        log.info("Retención del chat: {} conversaciones inactivas eliminadas (anteriores a {})", deleted, cutoff);
        long audits = toolAuditRepository.deleteByCreatedAtBefore(cutoff);
        log.info("Retención del chat: {} registros de auditoría de herramientas eliminados", audits);
        long actions = pendingActionRepository.deleteByCreatedAtBefore(cutoff);
        log.info("Retención del chat: {} acciones pendientes eliminadas", actions);
    }
}