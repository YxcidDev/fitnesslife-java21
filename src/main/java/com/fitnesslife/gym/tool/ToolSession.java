package com.fitnesslife.gym.tool;

import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;
import com.fitnesslife.gym.enums.Role;

public record ToolSession(String userId, Long identification, Role role, String conversationId,
                          ToolCallRecorder recorder) {

    private static final String KEY = "session";

    public Map<String, Object> asContext() {
        return Map.of(KEY, this);
    }

    public static ToolSession from(ToolContext context) {
        if (context == null || context.getContext() == null) {
            return null;
        }
        return context.getContext().get(KEY) instanceof ToolSession session ? session : null;
    }
}