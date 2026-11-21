package com.fitnesslife.gym.dto;

import com.fitnesslife.gym.enums.PendingActionStatus;

public record PendingActionResultResponse(String id, PendingActionStatus status, String message) {
}