package com.fitnesslife.gym.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fitnesslife.gym.config.ChatbotProperties;
import com.fitnesslife.gym.enums.PaymentStatus;
import com.fitnesslife.gym.enums.Role;
import com.fitnesslife.gym.model.Payment;
import com.fitnesslife.gym.repository.PaymentRepository;
import com.fitnesslife.gym.tool.ToolInputException;
import com.fitnesslife.gym.tool.ToolRunner.ToolResult;
import com.fitnesslife.gym.tool.ToolSession;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MembershipStatusService {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LastPayment(String status, String date, String planName) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MembershipStatus(boolean hasActivePlan, String planName, String validFrom, String validUntil,
                                   Long daysRemaining, String lastPlanExpiredOn, long pendingPaymentsLast3Days, LastPayment lastPayment,
                                   String renewalHint) {
    }

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int PENDING_WINDOW_DAYS = 3;

    private final PaymentRepository paymentRepository;
    private final ChatbotProperties properties;

    public ToolResult status(ToolSession session) {
        if (session.role() != Role.USER) {
            throw new ToolInputException("Esta consulta solo aplica a socios con cuenta de usuario.");
        }

        LocalDateTime now = LocalDateTime.now();
        Payment current = paymentRepository.findActiveByUserId(session.userId(), now).stream()
                .filter(payment -> payment.getValidUntil() != null)
                .max(Comparator.comparing(Payment::getValidUntil)).orElse(null);
        long pending = paymentRepository.countByUserIdAndStatusAndCreatedAtAfter(session.userId(),
                PaymentStatus.PENDING, now.minusDays(PENDING_WINDOW_DAYS));
        Payment last = paymentRepository.findFirstByUserIdOrderByCreatedAtDesc(session.userId()).orElse(null);

        LastPayment lastPayment = last == null ? null : new LastPayment(statusText(last.getStatus()),
                last.getCreatedAt() == null ? null : toGym(last.getCreatedAt()).format(DATE), last.getPlanName());

        String expiredOn = null;
        if (current == null && last != null && last.getValidUntil() != null && last.getValidUntil().isBefore(now)
                && (last.getStatus() == PaymentStatus.ACCEPTED || last.getStatus() == PaymentStatus.EXPIRED)) {
            expiredOn = toGym(last.getValidUntil()).format(DATE);
        }

        String hint = "El usuario puede renovar o contratar un plan desde la sección Planes de la aplicación.";
        MembershipStatus result;
        if (current == null) {
            result = new MembershipStatus(false, null, null, null, null, expiredOn, pending, lastPayment, hint);
        } else {
            long daysRemaining = Math.max(0, Duration.between(now, current.getValidUntil()).toDays());
            result = new MembershipStatus(true, current.getPlanName(),
                    current.getValidFrom() == null ? null : toGym(current.getValidFrom()).format(DATE_TIME),
                    toGym(current.getValidUntil()).format(DATE_TIME), daysRemaining, null, pending, lastPayment,
                    hint);
        }
        return new ToolResult(result, current == null ? "sin plan vigente" : "plan vigente");
    }

    private LocalDateTime toGym(LocalDateTime systemLocal) {
        return systemLocal.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneId.of(properties.tools().timezone())).toLocalDateTime();
    }

    private String statusText(PaymentStatus status) {
        if (status == null) {
            return "desconocido";
        }
        return switch (status) {
            case ACCEPTED -> "aprobado";
            case PENDING -> "pendiente";
            case REJECTED -> "rechazado";
            case FAILED -> "fallido";
            case CANCELLED -> "cancelado";
            case EXPIRED -> "vencido";
            case UNKNOWN -> "desconocido";
        };
    }
}