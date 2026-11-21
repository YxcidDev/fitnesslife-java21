package com.fitnesslife.gym.service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.fitnesslife.gym.config.ChatbotProperties;
import com.fitnesslife.gym.enums.ChatRole;
import com.fitnesslife.gym.enums.PendingActionStatus;
import com.fitnesslife.gym.enums.PendingActionType;
import com.fitnesslife.gym.enums.Role;
import com.fitnesslife.gym.exception.ChatException;
import com.fitnesslife.gym.exception.DomainException;
import com.fitnesslife.gym.model.ChatMessage;
import com.fitnesslife.gym.model.FunctionalTraining;
import com.fitnesslife.gym.model.PendingAction;
import com.fitnesslife.gym.model.User;
import com.fitnesslife.gym.repository.ChatConversationStore;
import com.fitnesslife.gym.repository.PendingActionRepository;
import com.fitnesslife.gym.tool.ToolInputException;
import com.fitnesslife.gym.tool.ToolRunner.ToolResult;
import com.fitnesslife.gym.tool.ToolSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PendingActionService {

    public record PendingActionResult(String id, PendingActionStatus status, String message) {
    }

    public record PreparedAction(boolean prepared, String summary, int expiresInMinutes, String instruction) {
    }

    private static final Pattern ID_PATTERN = Pattern
            .compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Locale SPANISH = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SPANISH);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("h:mm a", SPANISH);
    private static final Duration STUCK_AFTER = Duration.ofMinutes(5);

    private static final PendingActionStatus WAITING = PendingActionStatus.WAITING_CONFIRMATION;

    private final PendingActionRepository repository;
    private final MongoTemplate mongo;
    private final UserService userService;
    private final ClassEnrollmentService enrollmentService;
    private final ChatConversationStore conversationStore;
    private final ChatRateLimiter rateLimiter;
    private final ChatbotProperties properties;

    public ToolResult prepareForTool(ToolSession session, PendingActionType type, int classId) {
        if (session.role() != Role.USER) {
            throw new ToolInputException("Solo los socios pueden inscribirse en clases o cancelar su inscripción.");
        }
        User user = userService.getUserByIdOrThrow(session.userId());
        Instant now = Instant.now();

        FunctionalTraining training = type == PendingActionType.ENROLL_CLASS
                ? enrollmentService.validateEnroll(user, classId, now)
                : enrollmentService.validateCancel(user, classId, now);

        mongo.updateMulti(new Query(Criteria.where("userId").is(user.getId()).and("type").is(type)
                        .and("classId").is(classId).and("status").is(WAITING)),
                new Update().set("status", PendingActionStatus.CANCELLED).set("resolvedAt", now)
                        .set("resultMessage", "Reemplazada por una solicitud más reciente."),
                PendingAction.class);

        int max = properties.tools().maxPendingActions();
        if (repository.countByUserIdAndStatusAndExpiresAtAfter(user.getId(), WAITING, now) >= max) {
            throw new ToolInputException("Ya tienes " + max
                    + " solicitudes pendientes. Confírmalas o cancélalas antes de preparar otra.");
        }

        int ttl = properties.tools().pendingActionTtlMinutes();
        String label = classLabel(training);
        String verb = type == PendingActionType.ENROLL_CLASS ? "Inscribirte en " : "Cancelar tu inscripción en ";
        PendingAction action = repository.save(PendingAction.builder()
                .id(UUID.randomUUID().toString()).userId(user.getId()).conversationId(session.conversationId())
                .type(type).classId(classId).classLabel(label).summary(verb + label + ".")
                .status(WAITING).createdAt(now).expiresAt(now.plus(Duration.ofMinutes(ttl))).build());

        session.recorder().addPendingAction(action.getId());
        log.info("Acción {} preparada para el usuario {} (clase {})", type, user.getId(), classId);

        PreparedAction data = new PreparedAction(true, action.getSummary(), ttl,
                "AÚN NO SE HA HECHO NINGÚN CAMBIO. Dile al usuario que preparaste la solicitud y que debe confirmarla "
                        + "con el botón Confirmar que aparece debajo de tu mensaje.");
        return new ToolResult(data, "solicitud preparada");
    }

    public List<PendingAction> findWaitingForEmail(String email) {
        User user = userService.getUserOrThrow(email);
        return repository.findByUserIdAndStatusAndExpiresAtAfterOrderByCreatedAtAsc(user.getId(), WAITING,
                Instant.now());
    }

    public List<PendingAction> findWaitingByIds(String userId, Collection<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return repository.findByIdInAndUserIdAndStatus(ids, userId, WAITING);
    }

    public PendingActionResult confirm(String actionId, String email) {
        User user = userService.getUserOrThrow(email);
        rateLimiter.check(user.getId());
        Instant now = Instant.now();
        PendingAction claimed = claim(actionId, user.getId(), PendingActionStatus.EXECUTING, now);

        PendingActionStatus finalStatus;
        String message;
        try {
            if (claimed.getType() == PendingActionType.ENROLL_CLASS) {
                enrollmentService.enroll(user, claimed.getClassId());
                message = "Listo: quedaste inscrito en " + claimed.getClassLabel() + ".";
            } else {
                enrollmentService.cancel(user, claimed.getClassId());
                message = "Listo: cancelaste tu inscripción en " + claimed.getClassLabel()
                        + ". No hay ninguna sanción para tu plan.";
            }
            finalStatus = PendingActionStatus.EXECUTED;
        } catch (DomainException e) {
            finalStatus = PendingActionStatus.FAILED;
            message = e.getMessage();
        } catch (RuntimeException e) {
            log.error("Falló la ejecución de la acción {} ({})", claimed.getId(), e.getClass().getSimpleName());
            finalStatus = PendingActionStatus.FAILED;
            message = "No se pudo completar la acción. Inténtalo de nuevo en unos minutos.";
        }

        resolve(claimed.getId(), finalStatus, message);
        appendNote(claimed, message);
        log.info("Acción {} resuelta como {}", claimed.getId(), finalStatus);
        return new PendingActionResult(claimed.getId(), finalStatus, message);
    }

    public PendingActionResult decline(String actionId, String email) {
        User user = userService.getUserOrThrow(email);
        PendingAction claimed = claim(actionId, user.getId(), PendingActionStatus.CANCELLED, Instant.now());
        String message = "Cancelaste la solicitud: " + claimed.getSummary();
        resolve(claimed.getId(), PendingActionStatus.CANCELLED, message);
        appendNote(claimed, message);
        return new PendingActionResult(claimed.getId(), PendingActionStatus.CANCELLED, message);
    }

    private PendingAction claim(String actionId, String userId, PendingActionStatus target, Instant now) {
        if (actionId == null || !ID_PATTERN.matcher(actionId).matches()) {
            throw ChatException.actionNotFound();
        }
        Query query = new Query(Criteria.where("id").is(actionId).and("userId").is(userId)
                .and("status").is(WAITING).and("expiresAt").gt(now));
        Update update = new Update().set("status", target);
        update = target == PendingActionStatus.EXECUTING ? update.set("confirmedAt", now) : update.set("resolvedAt", now);

        PendingAction claimed = mongo.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true),
                PendingAction.class);
        if (claimed != null) {
            return claimed;
        }

        PendingAction existing = repository.findByIdAndUserId(actionId, userId)
                .orElseThrow(ChatException::actionNotFound);
        if (existing.getStatus() == WAITING) {
            resolveIfWaiting(existing.getId(), PendingActionStatus.EXPIRED, "La solicitud expiró.", now);
            throw ChatException.actionExpired();
        }
        throw ChatException.actionAlreadyResolved();
    }

    private void resolve(String id, PendingActionStatus status, String message) {
        mongo.updateFirst(new Query(Criteria.where("id").is(id)),
                new Update().set("status", status).set("resolvedAt", Instant.now()).set("resultMessage", message),
                PendingAction.class);
    }

    private void resolveIfWaiting(String id, PendingActionStatus status, String message, Instant now) {
        mongo.updateFirst(new Query(Criteria.where("id").is(id).and("status").is(WAITING)),
                new Update().set("status", status).set("resolvedAt", now).set("resultMessage", message),
                PendingAction.class);
    }

    private void appendNote(PendingAction action, String text) {
        if (!StringUtils.hasText(action.getConversationId())) {
            return;
        }
        Instant now = Instant.now();
        ChatMessage note = ChatMessage.builder().id(new ObjectId().toHexString()).role(ChatRole.ASSISTANT)
                .content(text).createdAt(now).realtime(true).build();
        conversationStore.appendNote(action.getConversationId(), action.getUserId(), note,
                properties.conversation().maxMessagesPerConversation(), now);
    }

    public void expireStale() {
        Instant now = Instant.now();
        long expired = mongo.updateMulti(new Query(Criteria.where("status").is(WAITING).and("expiresAt").lte(now)),
                new Update().set("status", PendingActionStatus.EXPIRED).set("resolvedAt", now)
                        .set("resultMessage", "La solicitud expiró."), PendingAction.class).getModifiedCount();
        long stuck = mongo.updateMulti(new Query(Criteria.where("status").is(PendingActionStatus.EXECUTING)
                        .and("confirmedAt").lte(now.minus(STUCK_AFTER))),
                new Update().set("status", PendingActionStatus.FAILED).set("resolvedAt", now)
                        .set("resultMessage", "No se pudo confirmar el resultado de la acción."),
                PendingAction.class).getModifiedCount();
        if (expired > 0 || stuck > 0) {
            log.info("Acciones pendientes: {} expiradas, {} atascadas marcadas como fallidas", expired, stuck);
        }
    }

    private String classLabel(FunctionalTraining training) {
        ZoneId zone = ZoneId.of(properties.tools().timezone());
        ZonedDateTime start = ZonedDateTime.ofInstant(training.getDatetime().toInstant(), zone);
        ZonedDateTime end = start.plusMinutes(training.durationMinutes());

        StringBuilder label = new StringBuilder("«").append(training.getNameTraining()).append("» el ")
                .append(DAY.format(start)).append(", de ").append(HOUR.format(start)).append(" a ")
                .append(HOUR.format(end));
        List<String> extra = new ArrayList<>();
        if (StringUtils.hasText(training.getRoom())) {
            extra.add(training.getRoom());
        }
        if (StringUtils.hasText(training.getInstructor())) {
            extra.add("con " + training.getInstructor());
        }
        if (!extra.isEmpty()) {
            label.append(" (").append(String.join(", ", extra)).append(")");
        }
        return label.toString();
    }
}