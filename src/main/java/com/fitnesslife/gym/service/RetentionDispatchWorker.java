package com.fitnesslife.gym.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.fitnesslife.gym.config.RetentionMailProperties;
import com.fitnesslife.gym.enums.RetentionAssignmentStatus;
import com.fitnesslife.gym.enums.RetentionDeliveryStatus;
import com.fitnesslife.gym.enums.RetentionDispatchStatus;
import com.fitnesslife.gym.exception.RetentionMailException;
import com.fitnesslife.gym.model.RetentionAssignment;
import com.fitnesslife.gym.model.RetentionCampaign;
import com.fitnesslife.gym.repository.RetentionCampaignRepository;
import com.fitnesslife.gym.repository.RetentionDispatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionDispatchWorker {

    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    private final RetentionCampaignRepository campaignRepository;
    private final RetentionDispatchRepository dispatchRepository;
    private final RetentionEmailService emailService;
    private final RetentionMailProperties properties;

    @Scheduled(fixedDelayString = "${retention.mail.poll-ms:5000}",
            initialDelayString = "${retention.mail.poll-ms:5000}")
    public void processPending() {
        try {
            dispatchRepository.reapStaleClaims(LocalDateTime.now().minusMinutes(properties.getStaleClaimMinutes()));

            for (RetentionCampaign campaign : campaignRepository.findByDispatchStatus(RetentionDispatchStatus.SENDING)) {
                try {
                    processCampaign(campaign);
                } catch (RuntimeException e) {
                    log.error("[RetentionDispatch] Error procesando la campaña {}", campaign.getId(), e);
                }
            }
        } catch (RuntimeException e) {
            log.error("[RetentionDispatch] Error en el ciclo de envío", e);
        }
    }

    private void processCampaign(RetentionCampaign campaign) {
        int processed = 0;
        int consecutiveFailures = 0;

        while (processed < properties.getBatchSize()) {
            if (!stillSending(campaign.getId())) {
                return;
            }

            Optional<RetentionAssignment> next = dispatchRepository.claimNext(campaign.getId(), LocalDateTime.now());
            if (next.isEmpty()) {
                finishIfDone(campaign.getId());
                return;
            }

            boolean ok = deliver(next.get(), campaign);
            processed++;
            consecutiveFailures = ok ? 0 : consecutiveFailures + 1;
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                log.warn("[RetentionDispatch] {} fallos seguidos en la campaña {}; se pausa hasta el próximo ciclo",
                        consecutiveFailures, campaign.getId());
                return;
            }
            if (!pause()) {
                return;
            }
        }
    }

    private boolean deliver(RetentionAssignment assignment, RetentionCampaign campaign) {
        String id = assignment.getId();

        if (!RetentionAssignmentStatus.PENDING.equals(assignment.getStatus())) {
            dispatchRepository.markSkipped(id, "La asignación ya no estaba pendiente");
            return true;
        }

        try {
            emailService.send(assignment, campaign);
            LocalDateTime now = LocalDateTime.now();
            dispatchRepository.markSent(id, now);
            if (!properties.isDryRun() && !"PROMOTION".equals(assignment.getAction())) {
                dispatchRepository.markExecuted(id, now);
            }
            return true;
        } catch (RetentionMailException e) {
            handleFailure(assignment, e.getMessage(), e.isRetryable());
        } catch (RuntimeException e) {
            log.error("[RetentionDispatch] Error inesperado enviando {}", id, e);
            handleFailure(assignment, "Error inesperado: " + e.getClass().getSimpleName(), true);
        }
        return false;
    }

    private void handleFailure(RetentionAssignment assignment, String error, boolean retryable) {
        if (retryable && assignment.getDeliveryAttempts() < properties.getMaxAttempts()) {
            log.warn("[RetentionDispatch] Fallo transitorio en {} (intento {}/{}): {}", assignment.getId(),
                    assignment.getDeliveryAttempts(), properties.getMaxAttempts(), error);
            dispatchRepository.requeue(assignment.getId(), error);
        } else {
            log.warn("[RetentionDispatch] Correo {} marcado como fallido: {}", assignment.getId(), error);
            dispatchRepository.markFailed(assignment.getId(), error);
        }
    }

    private void finishIfDone(String campaignId) {
        Map<RetentionDeliveryStatus, Integer> counts = dispatchRepository.countByDeliveryStatus(campaignId);
        boolean pending = counts.getOrDefault(RetentionDeliveryStatus.QUEUED, 0) > 0
                || counts.getOrDefault(RetentionDeliveryStatus.SENDING, 0) > 0;
        if (pending) {
            return;
        }
        RetentionDispatchStatus finalStatus = counts.getOrDefault(RetentionDeliveryStatus.FAILED, 0) > 0
                ? RetentionDispatchStatus.COMPLETED_WITH_ERRORS
                : RetentionDispatchStatus.COMPLETED;
        if (dispatchRepository.finishSending(campaignId, finalStatus, LocalDateTime.now())) {
            log.info("[RetentionDispatch] Campaña {} finalizada: {} ({})", campaignId, finalStatus, counts);
        }
    }

    private boolean stillSending(String campaignId) {
        return campaignRepository.findById(campaignId)
                .map(c -> RetentionDispatchStatus.SENDING.equals(c.getDispatchStatus()))
                .orElse(false);
    }

    private boolean pause() {
        long delay = properties.getDelayBetweenMs();
        if (delay <= 0) {
            return true;
        }
        try {
            Thread.sleep(delay);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}