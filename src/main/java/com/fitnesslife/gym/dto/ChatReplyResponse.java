package com.fitnesslife.gym.dto;

import java.util.List;

public record ChatReplyResponse(String conversationId, ChatMessageResponse reply, boolean grounded,
                                List<PendingActionResponse> pendingActions) {
}