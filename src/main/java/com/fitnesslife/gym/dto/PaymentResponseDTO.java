package com.fitnesslife.gym.dto;

import com.fitnesslife.gym.enums.PaymentStatus;
import java.time.LocalDateTime;

public record PaymentResponseDTO(
        String id,
        UserSummary user,
        PlanSummary plan,
        String externalInvoice,
        Double amount,
        String currency,
        PaymentStatus status,
        boolean active,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        String transactionId,
        String approvalCode,
        String bankName,
        String franchise,
        String responseCode,
        String responseText,
        String responseReason,
        LocalDateTime createdAt,
        LocalDateTime transactionDate) {

    public record UserSummary(
            String id,
            String name,
            String lastname,
            String email,
            Long identification) {
    }

    public record PlanSummary(
            String id,
            String planName,
            Integer durationDays,
            String badge) {
    }
}