package com.fitnesslife.gym.model;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import com.fitnesslife.gym.enums.RetentionAssignmentStatus;
import com.fitnesslife.gym.enums.RetentionDeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "retentionAssignments")
@CompoundIndexes({
        @CompoundIndex(name = "campaign_action_idx", def = "{'campaignId': 1, 'action': 1}"),
        @CompoundIndex(name = "campaign_risk_idx", def = "{'campaignId': 1, 'riskLevel': 1}"),
        @CompoundIndex(name = "campaign_status_idx", def = "{'campaignId': 1, 'status': 1}"),
        @CompoundIndex(name = "campaign_delivery_idx", def = "{'campaignId': 1, 'deliveryStatus': 1}"),
        @CompoundIndex(name = "delivery_claimedAt_idx", def = "{'deliveryStatus': 1, 'claimedAt': 1}"),
        @CompoundIndex(name = "user_createdAt_idx", def = "{'userId': 1, 'createdAt': -1}")
})
public class RetentionAssignment {

    @Id
    private String id;

    @Indexed
    private String campaignId;
    private String campaignName;

    @Indexed
    private String userId;
    private String name;
    private String email;
    private String phone;
    private String plan;
    private String riskLevel;
    private double confidence;

    private String action;
    private String actionLabel;

    private RetentionAssignmentStatus status;

    private RetentionDeliveryStatus deliveryStatus;
    private LocalDateTime sentAt;
    private LocalDateTime claimedAt;
    private int deliveryAttempts;
    private String deliveryError;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String updatedBy;

    public RetentionDeliveryStatus effectiveDeliveryStatus() {
        return deliveryStatus != null ? deliveryStatus : RetentionDeliveryStatus.NOT_SENT;
    }
}