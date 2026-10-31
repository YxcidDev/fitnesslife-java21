package com.fitnesslife.gym.exception;

import org.springframework.http.HttpStatus;

public class ChatException extends DomainException {

    private final Integer retryAfterSeconds;

    private ChatException(HttpStatus status, String code, String message, Integer retryAfterSeconds) {
        super(status, code, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public static ChatException notFound() {
        return new ChatException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "Conversación no encontrada", null);
    }

    public static ChatException rateLimited(int retryAfterSeconds) {
        return new ChatException(HttpStatus.TOO_MANY_REQUESTS, "CHAT_RATE_LIMITED",
                "Has enviado demasiados mensajes. Espera " + retryAfterSeconds + " segundos e inténtalo de nuevo.",
                retryAfterSeconds);
    }

    public static ChatException busy() {
        return new ChatException(HttpStatus.CONFLICT, "CHAT_CONVERSATION_BUSY",
                "Ya hay un mensaje en proceso en esta conversación. Espera la respuesta.", null);
    }

    public static ChatException conversationFull() {
        return new ChatException(HttpStatus.CONFLICT, "CHAT_CONVERSATION_FULL",
                "Esta conversación alcanzó su límite de mensajes. Inicia una nueva.", null);
    }

    public static ChatException conversationLimit() {
        return new ChatException(HttpStatus.CONFLICT, "CHAT_CONVERSATION_LIMIT",
                "Alcanzaste el máximo de conversaciones. Elimina alguna para crear otra.", null);
    }

    public static ChatException payloadTooLarge() {
        return new ChatException(HttpStatus.PAYLOAD_TOO_LARGE, "CHAT_PAYLOAD_TOO_LARGE",
                "La solicitud es demasiado grande", null);
    }

    public static ChatException unavailable() {
        return new ChatException(HttpStatus.SERVICE_UNAVAILABLE, "CHATBOT_UNAVAILABLE",
                "El asistente no está disponible en este momento.", null);
    }

    public static ChatException providerFailure() {
        return new ChatException(HttpStatus.BAD_GATEWAY, "CHAT_PROVIDER_ERROR",
                "El asistente no pudo procesar tu mensaje. Inténtalo de nuevo en unos minutos.", null);
    }

    public static ChatException providerBusy() {
        return new ChatException(HttpStatus.SERVICE_UNAVAILABLE, "CHAT_PROVIDER_BUSY",
                "El asistente está recibiendo muchas solicitudes. Inténtalo de nuevo en unos segundos.", null);
    }

    public static ChatException ingestionInProgress() {
        return new ChatException(HttpStatus.CONFLICT, "KNOWLEDGE_INGESTION_IN_PROGRESS",
                "Ya hay una ingesta en ejecución", null);
    }

    public static ChatException documentExists() {
        return new ChatException(HttpStatus.CONFLICT, "KNOWLEDGE_DOCUMENT_EXISTS",
                "Ya existe un documento con ese nombre en esa audiencia", null);
    }

    public static ChatException documentNotFound() {
        return new ChatException(HttpStatus.NOT_FOUND, "KNOWLEDGE_DOCUMENT_NOT_FOUND",
                "Documento no encontrado", null);
    }
}