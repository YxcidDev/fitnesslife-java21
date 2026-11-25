package com.fitnesslife.gym.model;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import com.fitnesslife.gym.enums.RetentionCampaignStatus;
import com.fitnesslife.gym.enums.RetentionDispatchStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "retentionCampaigns")
@CompoundIndexes({
        @CompoundIndex(name = "status_savedAt_idx", def = "{'status': 1, 'savedAt': -1}"),
        @CompoundIndex(name = "status_createdAt_idx", def = "{'status': 1, 'createdAt': 1}"),
        @CompoundIndex(name = "dispatchStatus_idx", def = "{'dispatchStatus': 1}")
})
public class RetentionCampaign {

    @Id
    private String id;

    private RetentionCampaignStatus status;
    private String name;
    private String notes;

    private String createdBy;
    private LocalDateTime createdAt;
    private String savedBy;
    private LocalDateTime savedAt;

    private RetentionDispatchStatus dispatchStatus;
    private String launchedBy;
    private LocalDateTime launchedAt;
    private LocalDateTime completedAt;

    private String solverStatus;
    private double objectiveValue;
    private Double relaxedObjective;
    private double totalCost;
    private double budget;

    private int assignmentCount;
    private int reminderCount;
    private int promotionCount;
    private int classInviteCount;

    private Parameters parameters;
    private List<SegmentSnapshot> segments;
    private List<List<Double>> allocation;
    private List<String> actionLabels;
    private List<Double> actionCosts;
    private List<ConstraintSnapshot> constraints;
    private List<String> messages;

    private List<AssignmentSnapshot> draftAssignments;

    public RetentionDispatchStatus effectiveDispatchStatus() {
        return dispatchStatus != null ? dispatchStatus : RetentionDispatchStatus.NOT_LAUNCHED;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Parameters {
        private double minConfidence;
        private List<Double> expectedMonths;
        private List<List<Double>> retentionLift;
        private double reminderCost;
        private double discountRate;
        private double classCost;
        private double averagePlanPrice;
        private double budget;
        private int classSlotsRequested;
        private int classSlotsApplied;
        private int maxPromotions;
        private int maxReminders;
        private double minHighRiskCoverage;
        private boolean integerMode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SegmentSnapshot {
        private String key;
        private String label;
        private int candidates;
        private double avgPlanPrice;
        private double expectedValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConstraintSnapshot {
        private String code;
        private String name;
        private String sense;
        private double used;
        private double limit;
        private double slack;
        private boolean binding;
        private double shadowPrice;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignmentSnapshot {
        private String userId;
        private String name;
        private String email;
        private String phone;
        private String plan;
        private String riskLevel;
        private double confidence;
        private String action;
        private String actionLabel;
    }
}