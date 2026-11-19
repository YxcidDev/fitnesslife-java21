package com.fitnesslife.gym.dto;

import com.fitnesslife.gym.enums.RetentionAssignmentStatus;
import jakarta.validation.constraints.NotNull;

public record RetentionAssignmentStatusRequestDTO(
        @NotNull(message = "El estado es obligatorio")
        RetentionAssignmentStatus status) {
}
