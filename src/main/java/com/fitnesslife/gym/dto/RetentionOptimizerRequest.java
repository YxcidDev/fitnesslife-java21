package com.fitnesslife.gym.dto;

import java.util.List;

public record RetentionOptimizerRequest(
        List<Integer> n,
        List<Double> v,
        List<List<Double>> p,
        List<Double> c,
        double budget,
        int classSlots,
        int maxPromotions,
        int maxReminders,
        double minHighRiskCoverage,
        boolean integer) {
}
