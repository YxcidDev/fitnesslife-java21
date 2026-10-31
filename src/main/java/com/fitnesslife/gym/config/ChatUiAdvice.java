package com.fitnesslife.gym.config;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import lombok.RequiredArgsConstructor;

@ControllerAdvice
@RequiredArgsConstructor
public class ChatUiAdvice {

    private final ChatbotProperties properties;

    @ModelAttribute("chatMaxMessageLength")
    public int chatMaxMessageLength() {
        return properties.limits().maxMessageLength();
    }

    @ModelAttribute("chatRetentionDays")
    public int chatRetentionDays() {
        return properties.conversation().retentionDays();
    }
}