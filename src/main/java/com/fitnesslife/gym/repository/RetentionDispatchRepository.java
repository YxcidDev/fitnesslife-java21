package com.fitnesslife.gym.repository;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import com.fitnesslife.gym.enums.RetentionAssignmentStatus;
import com.fitnesslife.gym.enums.RetentionCampaignStatus;
import com.fitnesslife.gym.enums.RetentionDeliveryStatus;
import com.fitnesslife.gym.enums.RetentionDispatchStatus;
import com.fitnesslife.gym.model.RetentionAssignment;
import com.fitnesslife.gym.model.RetentionCampaign;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class RetentionDispatchRepository {

    private static final String EMAIL_REGEX = "^[^@\\s]+@[^@\\s]+$";

    private final MongoTemplate mongoTemplate;

    public Optional<RetentionCampaign> claimLaunch(String campaignId, String adminEmail, LocalDateTime now) {
        Query query = new Query(Criteria.where("_id").is(campaignId)
                .and("status").is(RetentionCampaignStatus.SAVED)
                .and("dispatchStatus").in(RetentionDispatchStatus.NOT_LAUNCHED, null));
        Update update = new Update()
                .set("dispatchStatus", RetentionDispatchStatus.SENDING)
                .set("launchedBy", adminEmail)
                .set("launchedAt", now)
                .unset("completedAt");
        return Optional.ofNullable(mongoTemplate.findAndModify(query, update, returnNew(), RetentionCampaign.class));
    }

    public void revertLaunch(String campaignId) {
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(campaignId).and("dispatchStatus").is(RetentionDispatchStatus.SENDING)),
                new Update().set("dispatchStatus", RetentionDispatchStatus.NOT_LAUNCHED)
                        .unset("launchedBy").unset("launchedAt"),
                RetentionCampaign.class);
    }

    public boolean finishSending(String campaignId, RetentionDispatchStatus finalStatus, LocalDateTime now) {
        return mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(campaignId).and("dispatchStatus").is(RetentionDispatchStatus.SENDING)),
                new Update().set("dispatchStatus", finalStatus).set("completedAt", now),
                RetentionCampaign.class).getModifiedCount() > 0;
    }

    public boolean reopenForRetry(String campaignId) {
        return mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(campaignId)
                        .and("dispatchStatus").is(RetentionDispatchStatus.COMPLETED_WITH_ERRORS)),
                new Update().set("dispatchStatus", RetentionDispatchStatus.SENDING).unset("completedAt"),
                RetentionCampaign.class).getModifiedCount() > 0;
    }

    private Criteria notSent() {
        return new Criteria().orOperator(
                Criteria.where("deliveryStatus").is(RetentionDeliveryStatus.NOT_SENT),
                Criteria.where("deliveryStatus").is(null));
    }

    private Criteria deliverable(String campaignId) {
        return new Criteria().andOperator(
                Criteria.where("campaignId").is(campaignId),
                notSent(),
                Criteria.where("status").is(RetentionAssignmentStatus.PENDING),
                Criteria.where("email").regex(EMAIL_REGEX));
    }

    private Criteria undeliverable(String campaignId) {
        return new Criteria().andOperator(
                Criteria.where("campaignId").is(campaignId),
                notSent(),
                new Criteria().orOperator(
                        Criteria.where("status").ne(RetentionAssignmentStatus.PENDING),
                        Criteria.where("email").not().regex(EMAIL_REGEX)));
    }

    public Map<String, Integer> countDeliverableByAction(String campaignId) {
        Aggregation aggregation = Aggregation.newAggregation(RetentionAssignment.class,
                Aggregation.match(deliverable(campaignId)),
                Aggregation.group("action").count().as("n"));
        Map<String, Integer> result = new HashMap<>();
        mongoTemplate.aggregate(aggregation, RetentionAssignment.class, Document.class).getMappedResults()
                .forEach(d -> result.put(d.getString("_id"), ((Number) d.get("n")).intValue()));
        return result;
    }

    public int countUndeliverable(String campaignId) {
        return (int) mongoTemplate.count(new Query(undeliverable(campaignId)), RetentionAssignment.class);
    }

    public long queueDeliverable(String campaignId) {
        return mongoTemplate.updateMulti(new Query(deliverable(campaignId)),
                new Update().set("deliveryStatus", RetentionDeliveryStatus.QUEUED)
                        .set("deliveryAttempts", 0)
                        .unset("deliveryError"),
                RetentionAssignment.class).getModifiedCount();
    }

    public long skipUndeliverable(String campaignId) {
        return mongoTemplate.updateMulti(new Query(undeliverable(campaignId)),
                new Update().set("deliveryStatus", RetentionDeliveryStatus.SKIPPED)
                        .set("deliveryError", "Descartada o sin correo válido"),
                RetentionAssignment.class).getModifiedCount();
    }

    public Map<RetentionDeliveryStatus, Integer> countByDeliveryStatus(String campaignId) {
        Aggregation aggregation = Aggregation.newAggregation(RetentionAssignment.class,
                Aggregation.match(Criteria.where("campaignId").is(campaignId)),
                Aggregation.group("deliveryStatus").count().as("n"));
        Map<RetentionDeliveryStatus, Integer> result = new EnumMap<>(RetentionDeliveryStatus.class);
        mongoTemplate.aggregate(aggregation, RetentionAssignment.class, Document.class).getMappedResults()
                .forEach(d -> {
                    String raw = d.getString("_id");
                    RetentionDeliveryStatus status = raw == null ? RetentionDeliveryStatus.NOT_SENT
                            : RetentionDeliveryStatus.valueOf(raw);
                    result.merge(status, ((Number) d.get("n")).intValue(), Integer::sum);
                });
        return result;
    }

    public Optional<RetentionAssignment> claimNext(String campaignId, LocalDateTime now) {
        Query query = new Query(Criteria.where("campaignId").is(campaignId)
                .and("deliveryStatus").is(RetentionDeliveryStatus.QUEUED))
                .with(Sort.by(Sort.Order.asc("deliveryAttempts"), Sort.Order.asc("_id")));
        Update update = new Update()
                .set("deliveryStatus", RetentionDeliveryStatus.SENDING)
                .set("claimedAt", now)
                .inc("deliveryAttempts", 1);
        return Optional.ofNullable(mongoTemplate.findAndModify(query, update, returnNew(), RetentionAssignment.class));
    }

    public void markSent(String assignmentId, LocalDateTime now) {
        resolve(assignmentId, new Update()
                .set("deliveryStatus", RetentionDeliveryStatus.SENT)
                .set("sentAt", now)
                .unset("deliveryError")
                .unset("claimedAt"));
    }

    public void markExecuted(String assignmentId, LocalDateTime now) {
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(assignmentId)
                        .and("status").is(RetentionAssignmentStatus.PENDING)),
                new Update().set("status", RetentionAssignmentStatus.EXECUTED)
                        .set("updatedAt", now)
                        .set("updatedBy", "Envío automático"),
                RetentionAssignment.class);
    }

    public void markFailed(String assignmentId, String error) {
        resolve(assignmentId, new Update()
                .set("deliveryStatus", RetentionDeliveryStatus.FAILED)
                .set("deliveryError", truncate(error))
                .unset("claimedAt"));
    }

    public void markSkipped(String assignmentId, String reason) {
        resolve(assignmentId, new Update()
                .set("deliveryStatus", RetentionDeliveryStatus.SKIPPED)
                .set("deliveryError", truncate(reason))
                .unset("claimedAt"));
    }

    public void requeue(String assignmentId, String error) {
        resolve(assignmentId, new Update()
                .set("deliveryStatus", RetentionDeliveryStatus.QUEUED)
                .set("deliveryError", truncate(error))
                .unset("claimedAt"));
    }

    private void resolve(String assignmentId, Update update) {
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(assignmentId)
                        .and("deliveryStatus").is(RetentionDeliveryStatus.SENDING)),
                update, RetentionAssignment.class);
    }

    public boolean cancelCampaign(String campaignId, LocalDateTime now) {
        return finishSending(campaignId, RetentionDispatchStatus.CANCELLED, now);
    }

    public long cancelQueued(String campaignId) {
        return mongoTemplate.updateMulti(
                new Query(Criteria.where("campaignId").is(campaignId)
                        .and("deliveryStatus").is(RetentionDeliveryStatus.QUEUED)),
                new Update().set("deliveryStatus", RetentionDeliveryStatus.CANCELLED),
                RetentionAssignment.class).getModifiedCount();
    }

    public long requeueFailed(String campaignId) {
        return mongoTemplate.updateMulti(
                new Query(Criteria.where("campaignId").is(campaignId)
                        .and("deliveryStatus").is(RetentionDeliveryStatus.FAILED)),
                new Update().set("deliveryStatus", RetentionDeliveryStatus.QUEUED)
                        .set("deliveryAttempts", 0)
                        .unset("deliveryError"),
                RetentionAssignment.class).getModifiedCount();
    }

    public long reapStaleClaims(LocalDateTime olderThan) {
        return mongoTemplate.updateMulti(
                new Query(Criteria.where("deliveryStatus").is(RetentionDeliveryStatus.SENDING)
                        .and("claimedAt").lt(olderThan)),
                new Update().set("deliveryStatus", RetentionDeliveryStatus.QUEUED).unset("claimedAt"),
                RetentionAssignment.class).getModifiedCount();
    }

    private FindAndModifyOptions returnNew() {
        return FindAndModifyOptions.options().returnNew(true);
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > 300 ? text.substring(0, 300) : text;
    }
}
