package com.fitnesslife.gym.dto;

import java.time.Instant;
import java.util.List;
import com.fitnesslife.gym.enums.ChatRole;

public record ChatMessageResponse(
        String id,
        ChatRole role,
        String content,
        Instant createdAt,
        List<ChatSourceResponse> sources,
        boolean realtime) {
}