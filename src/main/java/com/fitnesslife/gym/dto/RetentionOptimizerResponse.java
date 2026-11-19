package com.fitnesslife.gym.dto;

import java.util.List;

public record RetentionOptimizerResponse(
        String status,
        double objectiveValue,
        double totalCost,
        List<List<Double>> x,
        List<ConstraintUsage> constraints,
        Double relaxedObjective,
        List<String> messages) {

    public record ConstraintUsage(
            String code,
            String name,
            String sense,
            double used,
            double limit,
            double slack,
            boolean binding,
            double shadowPrice) {
    }
}
