package com.fitnesslife.gym.dto;

import java.time.LocalDateTime;
import com.fitnesslife.gym.enums.RetentionDispatchStatus;

public record RetentionDispatchProgressDTO(
        String campaignId,
        RetentionDispatchStatus dispatchStatus,
        int total,
        int notSent,
        int queued,
        int sending,
        int sent,
        int failed,
        int skipped,
        int cancelled,
        int percent,
        boolean finished,
        String launchedBy,
        LocalDateTime launchedAt,
        LocalDateTime completedAt) {
}
