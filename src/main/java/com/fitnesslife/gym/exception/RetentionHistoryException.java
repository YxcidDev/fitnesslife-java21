package com.fitnesslife.gym.exception;

import org.springframework.http.HttpStatus;

public class RetentionHistoryException extends DomainException {

    private RetentionHistoryException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static RetentionHistoryException draftExpired() {
        return new RetentionHistoryException(HttpStatus.GONE, "RETENTION_DRAFT_EXPIRED",
                "Este resultado ya no está disponible (expiró o fue reemplazado por un cálculo más reciente). "
                        + "Vuelve a calcular la asignación antes de guardarla.");
    }

    public static RetentionHistoryException alreadySaved() {
        return new RetentionHistoryException(HttpStatus.CONFLICT, "RETENTION_ALREADY_SAVED",
                "Esta asignación ya fue guardada en el historial.");
    }

    public static RetentionHistoryException notOwner() {
        return new RetentionHistoryException(HttpStatus.FORBIDDEN, "RETENTION_NOT_OWNER",
                "Solo quien calculó esta asignación puede guardarla.");
    }

    public static RetentionHistoryException nothingToSave() {
        return new RetentionHistoryException(HttpStatus.UNPROCESSABLE_ENTITY, "RETENTION_NOTHING_TO_SAVE",
                "La asignación no contiene usuarios para guardar.");
    }

    public static RetentionHistoryException assignmentNotFound(String id) {
        return new RetentionHistoryException(HttpStatus.NOT_FOUND, "RETENTION_ASSIGNMENT_NOT_FOUND",
                "Asignación no encontrada: " + id);
    }
}
