package com.fitnesslife.gym.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import com.fitnesslife.gym.enums.ToolOutcome;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(collection = "toolAuditLogs")
@CompoundIndex(name = "user_created_idx", def = "{'userId': 1, 'createdAt': -1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolAuditLog {

    @Id
    private String id;

    private String userId;
    private String conversationId;
    private String toolName;
    private String arguments;
    private ToolOutcome outcome;
    private String resultSummary;
    private long durationMs;
    private Instant createdAt;
}