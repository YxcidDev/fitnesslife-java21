package com.fitnesslife.gym.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ChurnPredictionDTO(
        String userId,
        String name,
        String email,
        String phone,
        String plan,
        int planLevel,
        String profile,
        int nPayments,
        long daysSinceLastPayment,
        int nAttendances,
        double avgSessionMinutes,
        long daysSinceLastAtt,
        boolean participatesInClasses,
        String prediction,
        double predictionConfidence,
        String riskLevel,
        List<String> reasons,
        List<String> recommendations,
        LocalDateTime calculatedAt) {
}