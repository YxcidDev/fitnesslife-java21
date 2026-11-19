package com.fitnesslife.gym.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RetentionPlanResultDTO(
        String status,
        double objectiveValue,
        Double relaxedObjective,
        double totalCost,
        double budget,
        List<List<Double>> allocation,
        List<Segment> segments,
        List<Double> actionCosts,
        List<String> actionLabels,
        List<RetentionOptimizerResponse.ConstraintUsage> constraints,
        List<String> messages,
        List<UserAssignment> assignments,
        LocalDateTime calculatedAt,
        String runId) {

    public RetentionPlanResultDTO withRunId(String newRunId) {
        return new RetentionPlanResultDTO(status, objectiveValue, relaxedObjective, totalCost, budget,
                allocation, segments, actionCosts, actionLabels, constraints, messages, assignments,
                calculatedAt, newRunId);
    }

    public record Segment(
            String key,
            String label,
            int candidates,
            double avgPlanPrice,
            double expectedValue) {
    }

    public record UserAssignment(
            String userId,
            String name,
            String email,
            String phone,
            String plan,
            String riskLevel,
            double confidence,
            String action,
            String actionLabel) {
    }
}