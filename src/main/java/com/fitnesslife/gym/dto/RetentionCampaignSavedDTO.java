package com.fitnesslife.gym.dto;

import java.time.LocalDateTime;

public record RetentionCampaignSavedDTO(
        String id,
        String name,
        int assignmentCount,
        LocalDateTime savedAt) {
}
