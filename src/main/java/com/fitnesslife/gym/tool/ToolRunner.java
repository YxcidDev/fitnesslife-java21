package com.fitnesslife.gym.tool;

import java.time.Instant;
import java.util.function.Function;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;
import com.fitnesslife.gym.enums.ToolOutcome;
import com.fitnesslife.gym.model.ToolAuditLog;
import com.fitnesslife.gym.repository.ToolAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fitnesslife.gym.exception.DomainException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRunner {

    public record ToolResult(Object data, String summary) {
    }

    private static final int MAX_AUDIT_TEXT = 200;

    private final ToolAuditRepository auditRepository;

    public ToolResponse run(ToolContext toolContext, String toolName, String arguments,
                            Function<ToolSession, ToolResult> action) {
        ToolSession session = ToolSession.from(toolContext);
        if (session == null) {
            log.error("La herramienta {} se ejecutó sin contexto de sesión", toolName);
            return ToolResponse.failure("Herramienta no disponible.");
        }

        long started = System.nanoTime();
        if (!session.recorder().tryStartCall()) {
            audit(session, toolName, arguments, ToolOutcome.LIMIT_EXCEEDED, "límite por mensaje", started);
            return ToolResponse.failure("Se alcanzó el máximo de consultas para este mensaje. "
                    + "Responde con lo que ya tienes o pide al usuario que vuelva a preguntar.");
        }

        try {
            ToolResult result = action.apply(session);
            session.recorder().markSuccess();
            audit(session, toolName, arguments, ToolOutcome.SUCCESS, result.summary(), started);
            return ToolResponse.success(result.data());
        } catch (ToolInputException e) {
            audit(session, toolName, arguments, ToolOutcome.INVALID_ARGUMENTS, e.getMessage(), started);
            return ToolResponse.failure(e.getMessage());
        } catch (DomainException e) {
            audit(session, toolName, arguments, ToolOutcome.REJECTED, e.getMessage(), started);
            return ToolResponse.failure(e.getMessage());
        } catch (RuntimeException e) {
            log.error("Falló la herramienta {} ({})", toolName, e.getClass().getSimpleName());
            audit(session, toolName, arguments, ToolOutcome.ERROR, e.getClass().getSimpleName(), started);
            return ToolResponse.failure("No se pudo consultar la información en este momento.");
        }
    }

    private void audit(ToolSession session, String toolName, String arguments, ToolOutcome outcome, String summary,
                       long startedNanos) {
        try {
            auditRepository.save(ToolAuditLog.builder()
                    .userId(session.userId()).conversationId(session.conversationId()).toolName(toolName)
                    .arguments(clean(arguments)).outcome(outcome).resultSummary(clean(summary))
                    .durationMs((System.nanoTime() - startedNanos) / 1_000_000).createdAt(Instant.now()).build());
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar la auditoría de la herramienta {}", toolName);
        }
    }

    private String clean(String text) {
        if (text == null) {
            return "";
        }
        String single = text.replaceAll("[\\p{Cntrl}]", " ").strip();
        return single.length() > MAX_AUDIT_TEXT ? single.substring(0, MAX_AUDIT_TEXT) : single;
    }
}