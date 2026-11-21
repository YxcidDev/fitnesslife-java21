package com.fitnesslife.gym.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import com.fitnesslife.gym.enums.PendingActionStatus;
import com.fitnesslife.gym.enums.PendingActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(collection = "pendingActions")
@CompoundIndex(name = "user_status_idx", def = "{'userId': 1, 'status': 1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingAction {

    @Id
    private String id;

    private String userId;
    private String conversationId;
    private PendingActionType type;
    private int classId;

    private String classLabel;

    private String summary;

    private PendingActionStatus status;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant confirmedAt;
    private Instant resolvedAt;
    private String resultMessage;
}