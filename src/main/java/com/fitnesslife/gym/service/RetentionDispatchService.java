package com.fitnesslife.gym.service;

import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.fitnesslife.gym.config.RetentionMailProperties;
import com.fitnesslife.gym.dto.RetentionDispatchPreviewDTO;
import com.fitnesslife.gym.dto.RetentionDispatchProgressDTO;
import com.fitnesslife.gym.dto.RetentionLaunchRequestDTO;
import com.fitnesslife.gym.enums.RetentionCampaignStatus;
import com.fitnesslife.gym.enums.RetentionDeliveryStatus;
import com.fitnesslife.gym.enums.RetentionDispatchStatus;
import com.fitnesslife.gym.exception.RetentionDispatchException;
import com.fitnesslife.gym.model.RetentionCampaign;
import com.fitnesslife.gym.repository.RetentionCampaignRepository;
import com.fitnesslife.gym.repository.RetentionDispatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionDispatchService {

    private final RetentionCampaignRepository campaignRepository;
    private final RetentionDispatchRepository dispatchRepository;
    private final RetentionMailProperties mailProperties;

    public RetentionDispatchPreviewDTO preview(String campaignId) {
        RetentionCampaign campaign = loadSaved(campaignId);

        Map<String, Integer> byAction = dispatchRepository.countDeliverableByAction(campaignId);
        int reminders = byAction.getOrDefault("REMINDER", 0);
        int promotions = byAction.getOrDefault("PROMOTION", 0);
        int classInvites = byAction.getOrDefault("CLASS_INVITE", 0);
        int total = reminders + promotions + classInvites;

        RetentionCampaign.Parameters p = campaign.getParameters();
        double discountRate = p != null ? p.getDiscountRate() : 0;
        double discountPerPromotion = p != null ? p.getAveragePlanPrice() * discountRate : 0;
        double discountImpact = discountPerPromotion * promotions;
        double classCost = (p != null ? p.getClassCost() : 0) * classInvites;
        double reminderCost = (p != null ? p.getReminderCost() : 0) * reminders;

        boolean canLaunch = RetentionDispatchStatus.NOT_LAUNCHED.equals(campaign.effectiveDispatchStatus()) && total > 0;

        return new RetentionDispatchPreviewDTO(
                campaign.getId(),
                campaign.getName(),
                campaign.effectiveDispatchStatus(),
                canLaunch,
                total,
                reminders,
                promotions,
                classInvites,
                dispatchRepository.countUndeliverable(campaignId),
                Math.round(discountRate * 100),
                discountPerPromotion,
                discountImpact,
                classCost,
                reminderCost,
                discountImpact + classCost + reminderCost,
                mailProperties.isDryRun());
    }

    public RetentionDispatchProgressDTO launch(String campaignId, String adminEmail, RetentionLaunchRequestDTO request) {
        if (request == null || !request.acknowledged()) {
            throw RetentionDispatchException.notAcknowledged();
        }

        RetentionCampaign campaign = loadSaved(campaignId);
        if (!RetentionDispatchStatus.NOT_LAUNCHED.equals(campaign.effectiveDispatchStatus())) {
            throw RetentionDispatchException.alreadyLaunched();
        }

        int actual = totalDeliverable(campaignId);
        if (actual == 0) {
            throw RetentionDispatchException.nothingToSend();
        }
        if (actual != request.expectedRecipients()) {
            throw RetentionDispatchException.recipientsChanged(request.expectedRecipients(), actual);
        }

        LocalDateTime now = LocalDateTime.now();
        dispatchRepository.claimLaunch(campaignId, adminEmail, now)
                .orElseThrow(RetentionDispatchException::alreadyLaunched);

        long queued;
        long skipped;
        try {
            queued = dispatchRepository.queueDeliverable(campaignId);
            skipped = dispatchRepository.skipUndeliverable(campaignId);
        } catch (RuntimeException e) {
            log.error("[RetentionDispatch] Falló el encolado de {}; se revierte el lanzamiento", campaignId, e);
            dispatchRepository.revertLaunch(campaignId);
            throw e;
        }

        log.info("[RetentionDispatch] Campaña {} lanzada por {}: {} correos en cola, {} omitidos (dry-run={})",
                campaignId, adminEmail, queued, skipped, mailProperties.isDryRun());
        return progress(campaignId);
    }

    private int totalDeliverable(String campaignId) {
        return dispatchRepository.countDeliverableByAction(campaignId).values().stream()
                .mapToInt(Integer::intValue).sum();
    }

    /** Detiene los correos que aún no salieron. Los que ya se enviaron no se pueden recuperar. */
    public RetentionDispatchProgressDTO cancel(String campaignId, String adminEmail) {
        loadSaved(campaignId);
        if (!dispatchRepository.cancelCampaign(campaignId, LocalDateTime.now())) {
            throw RetentionDispatchException.notSending();
        }
        long cancelled = dispatchRepository.cancelQueued(campaignId);
        log.warn("[RetentionDispatch] Campaña {} detenida por {}: {} correos cancelados", campaignId, adminEmail,
                cancelled);
        return progress(campaignId);
    }

    /** Vuelve a encolar los correos fallidos de una campaña que terminó con errores. */
    public RetentionDispatchProgressDTO retryFailed(String campaignId, String adminEmail) {
        RetentionCampaign campaign = loadSaved(campaignId);
        if (!RetentionDispatchStatus.COMPLETED_WITH_ERRORS.equals(campaign.effectiveDispatchStatus())) {
            throw RetentionDispatchException.nothingToRetry();
        }
        long requeued = dispatchRepository.requeueFailed(campaignId);
        if (requeued == 0) {
            throw RetentionDispatchException.nothingToRetry();
        }
        dispatchRepository.reopenForRetry(campaignId);
        log.info("[RetentionDispatch] {} reintenta {} correos fallidos de la campaña {}", adminEmail, requeued,
                campaignId);
        return progress(campaignId);
    }

    public RetentionDispatchProgressDTO progress(String campaignId) {
        RetentionCampaign campaign = loadSaved(campaignId);
        Map<RetentionDeliveryStatus, Integer> counts = dispatchRepository.countByDeliveryStatus(campaignId);

        int notSent = counts.getOrDefault(RetentionDeliveryStatus.NOT_SENT, 0);
        int queued = counts.getOrDefault(RetentionDeliveryStatus.QUEUED, 0);
        int sending = counts.getOrDefault(RetentionDeliveryStatus.SENDING, 0);
        int sent = counts.getOrDefault(RetentionDeliveryStatus.SENT, 0);
        int failed = counts.getOrDefault(RetentionDeliveryStatus.FAILED, 0);
        int skipped = counts.getOrDefault(RetentionDeliveryStatus.SKIPPED, 0);
        int cancelled = counts.getOrDefault(RetentionDeliveryStatus.CANCELLED, 0);

        int total = notSent + queued + sending + sent + failed + skipped + cancelled;
        int launched = total - notSent;
        int resolved = sent + failed + skipped + cancelled;
        int percent = launched > 0 ? (int) Math.round(resolved * 100.0 / launched) : 0;

        RetentionDispatchStatus status = campaign.effectiveDispatchStatus();
        boolean finished = RetentionDispatchStatus.COMPLETED.equals(status)
                || RetentionDispatchStatus.COMPLETED_WITH_ERRORS.equals(status)
                || RetentionDispatchStatus.CANCELLED.equals(status);

        return new RetentionDispatchProgressDTO(campaignId, status, total, notSent, queued, sending, sent, failed,
                skipped, cancelled, percent, finished, campaign.getLaunchedBy(), campaign.getLaunchedAt(),
                campaign.getCompletedAt());
    }

    private RetentionCampaign loadSaved(String campaignId) {
        RetentionCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> RetentionDispatchException.campaignNotFound(campaignId));
        if (!RetentionCampaignStatus.SAVED.equals(campaign.getStatus())) {
            throw RetentionDispatchException.notSaved();
        }
        return campaign;
    }
}
