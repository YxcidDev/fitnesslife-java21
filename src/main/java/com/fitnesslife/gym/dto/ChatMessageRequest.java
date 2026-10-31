package com.fitnesslife.gym.dto;

import com.fitnesslife.gym.validation.ValidChatMessage;
import jakarta.validation.constraints.Pattern;

public record ChatMessageRequest(
        @ValidChatMessage String message,

        @Pattern(regexp = "^[a-fA-F0-9]{24}$", message = "El identificador de la conversación no es válido")
        String conversationId) {
}