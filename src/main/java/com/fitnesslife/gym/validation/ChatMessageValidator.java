package com.fitnesslife.gym.validation;

import com.fitnesslife.gym.config.ChatbotProperties;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ChatMessageValidator implements ConstraintValidator<ValidChatMessage, String> {

    private final ChatbotProperties properties;

    public ChatMessageValidator(ChatbotProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        int max = properties.limits().maxMessageLength();
        if (value == null || value.isBlank()) {
            return violation(context, "El mensaje no puede estar vacío");
        }
        if (value.strip().length() > max) {
            return violation(context, "El mensaje no puede superar los " + max + " caracteres");
        }
        return true;
    }

    private boolean violation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}