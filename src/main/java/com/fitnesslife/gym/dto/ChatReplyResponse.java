package com.fitnesslife.gym.dto;

public record ChatReplyResponse(String conversationId, ChatMessageResponse reply, boolean grounded) {
}