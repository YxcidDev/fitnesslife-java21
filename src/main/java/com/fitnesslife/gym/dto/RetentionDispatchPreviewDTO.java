package com.fitnesslife.gym.dto;

import com.fitnesslife.gym.enums.RetentionDispatchStatus;

public record RetentionDispatchPreviewDTO(
        String campaignId,
        String campaignName,
        RetentionDispatchStatus dispatchStatus,
        boolean canLaunch,
        int totalRecipients,
        int reminders,
        int promotions,
        int classInvites,
        int skipped,
        long discountPercent,
        double discountPerPromotion,
        double estimatedDiscountImpact,
        double estimatedClassCost,
        double estimatedReminderCost,
        double estimatedTotalCost,
        boolean dryRun) {
}
