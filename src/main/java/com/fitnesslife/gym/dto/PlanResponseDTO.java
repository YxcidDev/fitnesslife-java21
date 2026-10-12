package com.fitnesslife.gym.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PlanResponseDTO(
        String id,
        String planName,
        Double price,
        String currency,
        Integer durationDays,
        String badge,
        List<String> benefits,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}