package com.fitnesslife.gym.dto;

import java.time.Instant;
import com.fitnesslife.gym.enums.PendingActionType;

public record PendingActionResponse(String id, String conversationId, PendingActionType type, String summary,
                                    Instant expiresAt) {
}