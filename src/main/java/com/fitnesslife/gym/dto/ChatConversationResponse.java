package com.fitnesslife.gym.dto;

import java.time.Instant;

public record ChatConversationResponse(
        String id,
        String title,
        Instant createdAt,
        Instant updatedAt,
        int messageCount) {
}