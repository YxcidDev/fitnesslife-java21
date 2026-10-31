package com.fitnesslife.gym.model;

import java.time.Instant;
import java.util.List;
import com.fitnesslife.gym.enums.ChatRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String id;
    private ChatRole role;
    private String content;
    private Instant createdAt;
    private List<ChatSource> sources;
}