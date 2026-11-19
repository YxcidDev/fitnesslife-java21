package com.fitnesslife.gym.exception;

import org.springframework.http.HttpStatus;

public class RetentionDispatchException extends DomainException {

    private RetentionDispatchException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static RetentionDispatchException campaignNotFound(String id) {
        return new RetentionDispatchException(HttpStatus.NOT_FOUND, "RETENTION_CAMPAIGN_NOT_FOUND",
                "Campaña no encontrada: " + id);
    }

    public static RetentionDispatchException notSaved() {
        return new RetentionDispatchException(HttpStatus.CONFLICT, "RETENTION_CAMPAIGN_NOT_SAVED",
                "Primero debes guardar la asignación en el historial para poder lanzar la campaña.");
    }

    public static RetentionDispatchException alreadyLaunched() {
        return new RetentionDispatchException(HttpStatus.CONFLICT, "RETENTION_ALREADY_LAUNCHED",
                "Esta campaña ya fue lanzada (o está en proceso de envío).");
    }

    public static RetentionDispatchException notAcknowledged() {
        return new RetentionDispatchException(HttpStatus.UNPROCESSABLE_ENTITY, "RETENTION_NOT_ACKNOWLEDGED",
                "Debes confirmar que entiendes que el envío no se puede deshacer.");
    }

    public static RetentionDispatchException recipientsChanged(int expected, int actual) {
        return new RetentionDispatchException(HttpStatus.CONFLICT, "RETENTION_RECIPIENTS_CHANGED",
                "El número de destinatarios cambió (aprobaste " + expected + " y ahora son " + actual
                        + "). Revisa el resumen y confirma de nuevo.");
    }

    public static RetentionDispatchException nothingToSend() {
        return new RetentionDispatchException(HttpStatus.UNPROCESSABLE_ENTITY, "RETENTION_NOTHING_TO_SEND",
                "No hay destinatarios elegibles para esta campaña.");
    }

    public static RetentionDispatchException notSending() {
        return new RetentionDispatchException(HttpStatus.CONFLICT, "RETENTION_NOT_SENDING",
                "La campaña no tiene un envío en curso.");
    }

    public static RetentionDispatchException nothingToRetry() {
        return new RetentionDispatchException(HttpStatus.CONFLICT, "RETENTION_NOTHING_TO_RETRY",
                "No hay correos fallidos para reintentar en esta campaña.");
    }
}