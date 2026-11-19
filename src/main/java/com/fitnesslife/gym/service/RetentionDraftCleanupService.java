package com.fitnesslife.gym.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionDraftCleanupService {

    private final RetentionHistoryService historyService;

    @Scheduled(cron = "0 */30 * * * *")
    public void cleanStaleDrafts() {
        try {
            long deleted = historyService.deleteStaleDrafts();
            if (deleted > 0) {
                log.info("[RetentionCleanup] {} borradores de optimización eliminados", deleted);
            }
        } catch (Exception e) {
            log.error("[RetentionCleanup] Error limpiando borradores: {}", e.getMessage(), e);
        }
    }
}