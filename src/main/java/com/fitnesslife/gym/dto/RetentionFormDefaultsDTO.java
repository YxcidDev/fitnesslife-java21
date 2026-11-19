package com.fitnesslife.gym.dto;

import java.util.List;

public record RetentionFormDefaultsDTO(
        RetentionOptimizationRequestDTO parameters,
        List<RetentionPlanResultDTO.Segment> segments,
        double averagePlanPrice) {
}
