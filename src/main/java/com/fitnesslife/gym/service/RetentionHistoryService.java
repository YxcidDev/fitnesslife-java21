package com.fitnesslife.gym.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import com.fitnesslife.gym.dto.RetentionCampaignSaveRequestDTO;
import com.fitnesslife.gym.dto.RetentionCampaignSavedDTO;
import com.fitnesslife.gym.dto.RetentionOptimizationRequestDTO;
import com.fitnesslife.gym.dto.RetentionPlanResultDTO;
import com.fitnesslife.gym.enums.RetentionAssignmentStatus;
import com.fitnesslife.gym.enums.RetentionCampaignStatus;
import com.fitnesslife.gym.enums.RetentionDeliveryStatus;
import com.fitnesslife.gym.enums.RetentionDispatchStatus;
import com.fitnesslife.gym.exception.RetentionHistoryException;
import com.fitnesslife.gym.model.RetentionAssignment;
import com.fitnesslife.gym.model.RetentionCampaign;
import com.fitnesslife.gym.model.RetentionCampaign.AssignmentSnapshot;
import com.fitnesslife.gym.repository.RetentionAssignmentRepository;
import com.fitnesslife.gym.repository.RetentionCampaignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionHistoryService {

    private static final int DRAFT_TTL_HOURS = 2;
    private static final int CAMPAIGN_OPTIONS_LIMIT = 100;
    private static final DateTimeFormatter NAME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final RetentionCampaignRepository campaignRepository;
    private final RetentionAssignmentRepository assignmentRepository;
    private final MongoTemplate mongoTemplate;

    public record HistoryPage(
            Page<RetentionAssignment> assignments,
            List<RetentionCampaign> campaigns,
            RetentionCampaign selectedCampaign) {
    }

    public String createDraft(String adminEmail, RetentionOptimizationRequestDTO req, int appliedSlots,
                              double averagePlanPrice, RetentionPlanResultDTO result) {

        campaignRepository.deleteByStatusAndCreatedBy(RetentionCampaignStatus.DRAFT, adminEmail);

        List<AssignmentSnapshot> snapshots = result.assignments().stream()
                .map(a -> AssignmentSnapshot.builder()
                        .userId(a.userId())
                        .name(a.name())
                        .email(a.email())
                        .phone(a.phone())
                        .plan(a.plan())
                        .riskLevel(a.riskLevel())
                        .confidence(a.confidence())
                        .action(a.action())
                        .actionLabel(a.actionLabel())
                        .build())
                .toList();

        RetentionCampaign draft = RetentionCampaign.builder()
                .status(RetentionCampaignStatus.DRAFT)
                .createdBy(adminEmail)
                .createdAt(LocalDateTime.now())
                .solverStatus(result.status())
                .objectiveValue(result.objectiveValue())
                .relaxedObjective(result.relaxedObjective())
                .totalCost(result.totalCost())
                .budget(result.budget())
                .assignmentCount(snapshots.size())
                .reminderCount(countByAction(snapshots, "REMINDER"))
                .promotionCount(countByAction(snapshots, "PROMOTION"))
                .classInviteCount(countByAction(snapshots, "CLASS_INVITE"))
                .parameters(RetentionCampaign.Parameters.builder()
                        .minConfidence(req.minConfidence())
                        .expectedMonths(req.expectedMonths())
                        .retentionLift(req.retentionLift())
                        .reminderCost(req.reminderCost())
                        .discountRate(req.discountRate())
                        .classCost(req.classCost())
                        .averagePlanPrice(averagePlanPrice)
                        .budget(req.budget())
                        .classSlotsRequested(req.classSlots())
                        .classSlotsApplied(appliedSlots)
                        .maxPromotions(req.maxPromotions())
                        .maxReminders(req.maxReminders())
                        .minHighRiskCoverage(req.minHighRiskCoverage())
                        .integerMode(!Boolean.FALSE.equals(req.integer()))
                        .build())
                .segments(result.segments().stream()
                        .map(s -> RetentionCampaign.SegmentSnapshot.builder()
                                .key(s.key())
                                .label(s.label())
                                .candidates(s.candidates())
                                .avgPlanPrice(s.avgPlanPrice())
                                .expectedValue(s.expectedValue())
                                .build())
                        .toList())
                .allocation(result.allocation())
                .actionLabels(result.actionLabels())
                .actionCosts(result.actionCosts())
                .constraints(result.constraints().stream()
                        .map(c -> RetentionCampaign.ConstraintSnapshot.builder()
                                .code(c.code())
                                .name(c.name())
                                .sense(c.sense())
                                .used(c.used())
                                .limit(c.limit())
                                .slack(c.slack())
                                .binding(c.binding())
                                .shadowPrice(c.shadowPrice())
                                .build())
                        .toList())
                .messages(result.messages())
                .draftAssignments(snapshots)
                .build();

        RetentionCampaign saved = campaignRepository.save(draft);
        log.info("[RetentionHistory] Borrador {} creado por {} con {} asignaciones",
                saved.getId(), adminEmail, snapshots.size());
        return saved.getId();
    }

    private int countByAction(List<AssignmentSnapshot> snapshots, String action) {
        return (int) snapshots.stream().filter(s -> action.equals(s.getAction())).count();
    }

    public RetentionCampaignSavedDTO saveCampaign(String campaignId, String adminEmail,
                                                  RetentionCampaignSaveRequestDTO request) {

        RetentionCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(RetentionHistoryException::draftExpired);

        if (!Objects.equals(campaign.getCreatedBy(), adminEmail)) {
            throw RetentionHistoryException.notOwner();
        }
        if (RetentionCampaignStatus.SAVED.equals(campaign.getStatus())) {
            throw RetentionHistoryException.alreadySaved();
        }

        List<AssignmentSnapshot> drafts = campaign.getDraftAssignments();
        if (drafts == null || drafts.isEmpty()) {
            throw RetentionHistoryException.nothingToSave();
        }

        LocalDateTime now = LocalDateTime.now();
        String name = resolveName(request, now);

        List<RetentionAssignment> documents = new ArrayList<>(drafts.size());
        for (AssignmentSnapshot d : drafts) {
            documents.add(RetentionAssignment.builder()
                    .id(campaignId + "_" + d.getUserId())
                    .campaignId(campaignId)
                    .campaignName(name)
                    .userId(d.getUserId())
                    .name(d.getName())
                    .email(d.getEmail())
                    .phone(d.getPhone())
                    .plan(d.getPlan())
                    .riskLevel(d.getRiskLevel())
                    .confidence(d.getConfidence())
                    .action(d.getAction())
                    .actionLabel(d.getActionLabel())
                    .status(RetentionAssignmentStatus.PENDING)
                    .deliveryStatus(RetentionDeliveryStatus.NOT_SENT)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        }
        assignmentRepository.saveAll(documents);

        campaign.setName(name);
        campaign.setNotes(request != null && request.notes() != null && !request.notes().isBlank()
                ? request.notes().trim()
                : null);
        campaign.setStatus(RetentionCampaignStatus.SAVED);
        campaign.setDispatchStatus(RetentionDispatchStatus.NOT_LAUNCHED);
        campaign.setSavedBy(adminEmail);
        campaign.setSavedAt(now);
        campaign.setAssignmentCount(documents.size());
        campaign.setDraftAssignments(null);
        campaignRepository.save(campaign);

        log.info("[RetentionHistory] Campaña {} guardada por {} con {} asignaciones",
                campaignId, adminEmail, documents.size());
        return new RetentionCampaignSavedDTO(campaignId, name, documents.size(), now);
    }

    private String resolveName(RetentionCampaignSaveRequestDTO request, LocalDateTime now) {
        if (request != null && request.name() != null && !request.name().isBlank()) {
            return request.name().trim();
        }
        return "Campaña " + NAME_FORMAT.format(now);
    }

    public HistoryPage getHistory(String campaignId, String action, String risk,
                                  RetentionAssignmentStatus status, RetentionDeliveryStatus delivery, String search, int page, int size) {

        List<Criteria> filters = new ArrayList<>();
        if (hasText(campaignId)) {
            filters.add(Criteria.where("campaignId").is(campaignId));
        }
        if (hasText(action)) {
            filters.add(Criteria.where("action").is(action));
        }
        if (hasText(risk)) {
            filters.add(Criteria.where("riskLevel").is(risk));
        }
        if (status != null) {
            filters.add(Criteria.where("status").is(status));
        }
        if (delivery != null) {
            if (RetentionDeliveryStatus.NOT_SENT.equals(delivery)) {
                // registros anteriores al envío de correos no tienen el campo: también cuentan como "sin enviar"
                filters.add(new Criteria().orOperator(
                        Criteria.where("deliveryStatus").is(RetentionDeliveryStatus.NOT_SENT),
                        Criteria.where("deliveryStatus").is(null)));
            } else {
                filters.add(Criteria.where("deliveryStatus").is(delivery));
            }
        }
        String safeSearch = SearchTermSanitizer.sanitize(search);
        if (hasText(safeSearch)) {
            filters.add(new Criteria().orOperator(
                    Criteria.where("name").regex(safeSearch, "i"),
                    Criteria.where("email").regex(safeSearch, "i")));
        }

        Query query = new Query();
        if (!filters.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(filters.toArray(new Criteria[0])));
        }

        Pageable pageable = PageRequest.of(Math.max(page, 0), size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("name")));

        List<RetentionAssignment> rows = mongoTemplate.find(Query.of(query).with(pageable), RetentionAssignment.class);
        Page<RetentionAssignment> assignments = PageableExecutionUtils.getPage(rows, pageable,
                () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), RetentionAssignment.class));

        List<RetentionCampaign> campaigns = campaignRepository.findByStatus(RetentionCampaignStatus.SAVED,
                PageRequest.of(0, CAMPAIGN_OPTIONS_LIMIT, Sort.by(Sort.Direction.DESC, "savedAt")));

        RetentionCampaign selected = null;
        if (hasText(campaignId)) {
            selected = campaigns.stream().filter(c -> campaignId.equals(c.getId())).findFirst()
                    .orElseGet(() -> campaignRepository.findById(campaignId)
                            .filter(c -> RetentionCampaignStatus.SAVED.equals(c.getStatus()))
                            .orElse(null));
        }

        return new HistoryPage(assignments, campaigns, selected);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public RetentionAssignment updateStatus(String assignmentId, RetentionAssignmentStatus status, String adminEmail) {
        RetentionAssignment previous = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> RetentionHistoryException.assignmentNotFound(assignmentId));

        RetentionAssignment updated = mongoTemplate.findAndModify(
                new Query(Criteria.where("_id").is(assignmentId)),
                new Update().set("status", status)
                        .set("updatedAt", LocalDateTime.now())
                        .set("updatedBy", adminEmail),
                FindAndModifyOptions.options().returnNew(true),
                RetentionAssignment.class);
        if (updated == null) {
            throw RetentionHistoryException.assignmentNotFound(assignmentId);
        }

        log.info("[RetentionHistory] Asignación {} de {} a {} por {}", assignmentId, previous.getStatus(), status,
                adminEmail);
        return updated;
    }

    public long deleteStaleDrafts() {
        return campaignRepository.deleteByStatusAndCreatedAtBefore(
                RetentionCampaignStatus.DRAFT, LocalDateTime.now().minusHours(DRAFT_TTL_HOURS));
    }
}