package com.fitnesslife.gym.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import com.fitnesslife.gym.config.RetentionDefaults;
import com.fitnesslife.gym.dto.RetentionFormDefaultsDTO;
import com.fitnesslife.gym.dto.RetentionOptimizationRequestDTO;
import com.fitnesslife.gym.dto.RetentionOptimizerRequest;
import com.fitnesslife.gym.dto.RetentionOptimizerResponse;
import com.fitnesslife.gym.dto.RetentionPlanResultDTO;
import com.fitnesslife.gym.dto.RetentionPlanResultDTO.Segment;
import com.fitnesslife.gym.dto.RetentionPlanResultDTO.UserAssignment;
import com.fitnesslife.gym.exception.InvalidRetentionParametersException;
import com.fitnesslife.gym.enums.ClassesStatus;
import com.fitnesslife.gym.exception.OptimizerException;
import com.fitnesslife.gym.model.ChurnPrediction;
import com.fitnesslife.gym.model.FunctionalTraining;
import com.fitnesslife.gym.model.Plan;
import com.fitnesslife.gym.repository.ChurnPredictionRepository;
import com.fitnesslife.gym.repository.FunctionalTrainingRepository;
import com.fitnesslife.gym.repository.PlanRepository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RetentionOptimizationService {

    private static final String[] SEGMENT_KEYS = { "LOW", "MEDIUM", "HIGH" };
    private static final String[] SEGMENT_LABELS = { "Riesgo bajo", "Riesgo medio", "Riesgo alto" };
    private static final String[] ACTION_KEYS = { "REMINDER", "PROMOTION", "CLASS_INVITE" };
    private static final List<String> ACTION_LABELS = List.of(
            "Recordatorio de asistencia", "Promoción de renovación", "Invitación a clase grupal");

    private final ChurnPredictionRepository churnPredictionRepository;
    private final PlanRepository planRepository;
    private final FunctionalTrainingRepository functionalTrainingRepository;
    private final RetentionHistoryService historyService;
    private final RestClient optimizerRestClient;
    private final RetentionDefaults defaults;
    private final String optimizerUrl;

    public RetentionOptimizationService(
            ChurnPredictionRepository churnPredictionRepository,
            PlanRepository planRepository,
            FunctionalTrainingRepository functionalTrainingRepository,
            RetentionHistoryService historyService,
            @Qualifier("optimizerRestClient") RestClient optimizerRestClient,
            RetentionDefaults defaults,
            @Value("${optimizer.api.url}") String optimizerUrl) {
        this.churnPredictionRepository = churnPredictionRepository;
        this.planRepository = planRepository;
        this.functionalTrainingRepository = functionalTrainingRepository;
        this.historyService = historyService;
        this.optimizerRestClient = optimizerRestClient;
        this.defaults = defaults;
        this.optimizerUrl = optimizerUrl;
    }

    private record SegmentData(List<ChurnPrediction> users, double avgPrice) {
    }

    public RetentionFormDefaultsDTO getFormDefaults() {
        RetentionOptimizationRequestDTO params = defaultRequest();
        Map<String, Double> prices = loadPlanPrices();
        double globalAvg = averageOf(prices);
        List<SegmentData> data = buildSegmentData(params.minConfidence(), prices, globalAvg);
        return new RetentionFormDefaultsDTO(params, toSegments(data, params.expectedMonths()), globalAvg);
    }

    private RetentionOptimizationRequestDTO defaultRequest() {
        List<Double> lift = defaults.getRetentionLift();
        if (lift == null || lift.size() != 9) {
            throw new InvalidRetentionParametersException(
                    "retention.defaults.retention-lift debe contener exactamente 9 valores");
        }
        if (defaults.getExpectedMonths() == null || defaults.getExpectedMonths().size() != 3) {
            throw new InvalidRetentionParametersException(
                    "retention.defaults.expected-months debe contener exactamente 3 valores");
        }
        List<List<Double>> matrix = List.of(
                new ArrayList<>(lift.subList(0, 3)),
                new ArrayList<>(lift.subList(3, 6)),
                new ArrayList<>(lift.subList(6, 9)));

        return new RetentionOptimizationRequestDTO(
                defaults.getMinConfidence(),
                new ArrayList<>(defaults.getExpectedMonths()),
                matrix,
                defaults.getReminderCost(),
                defaults.getDiscountRate(),
                defaults.getClassCost(),
                defaults.getBudget(),
                computeFreeClassSlots(),
                defaults.getMaxPromotions(),
                defaults.getMaxReminders(),
                defaults.getMinHighRiskCoverage(),
                Boolean.TRUE);
    }

    private int computeFreeClassSlots() {
        Date now = new Date();
        long free = 0;
        for (FunctionalTraining training : functionalTrainingRepository.findAll()) {
            if (!ClassesStatus.ACTIVE.equals(training.getStatus())) {
                continue;
            }
            if (training.getDatetime() != null && !training.getDatetime().after(now)) {
                continue;
            }
            int enrolled = training.getUserIds() == null ? 0 : training.getUserIds().size();
            free += Math.max(0, training.getMaximumCapacity() - enrolled);
        }
        return (int) Math.min(free, Integer.MAX_VALUE);
    }

    public RetentionPlanResultDTO optimize(RetentionOptimizationRequestDTO req, String adminEmail) {
        long start = System.currentTimeMillis();
        log.info("[Retention] Iniciando optimización de acciones de retención ({})", adminEmail);

        int freeSlots = computeFreeClassSlots();
        int effectiveSlots = Math.min(req.classSlots(), freeSlots);
        List<String> notes = new ArrayList<>();
        if (req.classSlots() > freeSlots) {
            notes.add("Los cupos de clases se ajustaron a " + freeSlots
                    + " (cupos libres reales en clases activas); solicitaste " + req.classSlots() + ".");
        }

        Map<String, Double> prices = loadPlanPrices();
        double globalAvg = averageOf(prices);
        if (globalAvg <= 0) {
            throw new InvalidRetentionParametersException(
                    "No hay planes con precio registrado: no se puede calcular el valor esperado de retención");
        }

        List<SegmentData> data = buildSegmentData(req.minConfidence(), prices, globalAvg);
        List<Segment> segments = toSegments(data, req.expectedMonths());

        List<Double> costs = List.of(req.reminderCost(), globalAvg * req.discountRate(), req.classCost());

        RetentionOptimizerRequest payload = new RetentionOptimizerRequest(
                segments.stream().map(Segment::candidates).toList(),
                segments.stream().map(Segment::expectedValue).toList(),
                req.retentionLift(),
                costs,
                req.budget(),
                effectiveSlots,
                req.maxPromotions(),
                req.maxReminders(),
                req.minHighRiskCoverage(),
                !Boolean.FALSE.equals(req.integer()));

        RetentionOptimizerResponse response = callOptimizer(payload);

        List<UserAssignment> assignments = "Optimal".equals(response.status())
                ? assignUsers(data, response.x())
                : List.of();

        log.info("[Retention] Estado={} Z={} costo={} usuarios asignados={} ({} ms)",
                response.status(), response.objectiveValue(), response.totalCost(),
                assignments.size(), System.currentTimeMillis() - start);

        List<String> messages = new ArrayList<>(notes);
        if (response.messages() != null) {
            messages.addAll(response.messages());
        }

        RetentionPlanResultDTO result = new RetentionPlanResultDTO(
                response.status(),
                response.objectiveValue(),
                response.relaxedObjective(),
                response.totalCost(),
                req.budget(),
                response.x(),
                segments,
                costs,
                ACTION_LABELS,
                response.constraints() != null ? response.constraints() : List.of(),
                messages,
                assignments,
                LocalDateTime.now(),
                null);

        if (!assignments.isEmpty()) {
            try {
                String runId = historyService.createDraft(adminEmail, req, effectiveSlots, globalAvg, result);
                return result.withRunId(runId);
            } catch (RuntimeException e) {
                log.error("[Retention] No se pudo crear el borrador para el historial", e);
            }
        }
        return result;
    }

    private RetentionOptimizerResponse callOptimizer(RetentionOptimizerRequest payload) {
        try {
            RetentionOptimizerResponse response = optimizerRestClient.post()
                    .uri(optimizerUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(RetentionOptimizerResponse.class);

            if (response == null || response.x() == null) {
                throw OptimizerException.emptyResponse();
            }
            return response;

        } catch (ResourceAccessException e) {
            log.error("[Retention] Microservicio no alcanzable en {}: {}", optimizerUrl, e.getMessage());
            throw OptimizerException.unavailable();
        } catch (RestClientResponseException e) {
            log.error("[Retention] El optimizador respondió HTTP {}: {}",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
            throw OptimizerException.badResponse(e.getStatusCode().value());
        } catch (RestClientException e) {
            log.error("[Retention] Error inesperado llamando al optimizador", e);
            throw OptimizerException.unavailable();
        }
    }

    private List<SegmentData> buildSegmentData(double minConfidence, Map<String, Double> prices, double globalAvg) {
        List<List<ChurnPrediction>> buckets = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

        for (ChurnPrediction prediction : churnPredictionRepository.findAll()) {
            int idx = segmentIndex(prediction.getRiskLevel());
            if (idx < 0 || prediction.getPredictionConfidence() < minConfidence) {
                continue;
            }
            buckets.get(idx).add(prediction);
        }

        List<SegmentData> result = new ArrayList<>(3);
        for (List<ChurnPrediction> users : buckets) {
            double sum = 0;
            int count = 0;
            for (ChurnPrediction u : users) {
                Double price = prices.get(planKey(u.getPlan()));
                if (price != null) {
                    sum += price;
                    count++;
                }
            }
            result.add(new SegmentData(users, count > 0 ? sum / count : globalAvg));
        }
        return result;
    }

    private List<Segment> toSegments(List<SegmentData> data, List<Double> expectedMonths) {
        List<Segment> segments = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            SegmentData d = data.get(i);
            segments.add(new Segment(
                    SEGMENT_KEYS[i],
                    SEGMENT_LABELS[i],
                    d.users().size(),
                    d.avgPrice(),
                    d.avgPrice() * expectedMonths.get(i)));
        }
        return segments;
    }

    private Map<String, Double> loadPlanPrices() {
        Map<String, Double> prices = new HashMap<>();
        for (Plan plan : planRepository.findAll()) {
            Double price = plan.getPrice();
            if (plan.getPlanName() != null && price != null) {
                prices.put(planKey(plan.getPlanName()), price);
            }
        }
        return prices;
    }

    private double averageOf(Map<String, Double> prices) {
        return prices.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private String planKey(String planName) {
        return planName == null ? "" : planName.trim().toLowerCase(Locale.ROOT);
    }

    private int segmentIndex(String riskLevel) {
        if (riskLevel == null) {
            return -1;
        }
        return switch (riskLevel) {
            case "LOW" -> 0;
            case "MEDIUM" -> 1;
            case "HIGH" -> 2;
            default -> -1;
        };
    }

    private List<UserAssignment> assignUsers(List<SegmentData> data, List<List<Double>> x) {
        Comparator<ChurnPrediction> byConfidence = Comparator
                .comparingDouble(ChurnPrediction::getPredictionConfidence).reversed();

        Comparator<ChurnPrediction> forClass = Comparator
                .comparing(ChurnPrediction::isParticipatesInClasses)
                .thenComparing(byConfidence);

        Comparator<ChurnPrediction> forPromotion = Comparator
                .comparingLong(ChurnPrediction::getDaysSinceLastPayment).reversed()
                .thenComparing(byConfidence);

        List<UserAssignment> assignments = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            List<ChurnPrediction> pool = new ArrayList<>(data.get(i).users());
            List<Double> row = x.get(i);

            addAssignments(assignments, take(pool, toCount(row.get(2)), forClass), 2, SEGMENT_KEYS[i]);
            addAssignments(assignments, take(pool, toCount(row.get(1)), forPromotion), 1, SEGMENT_KEYS[i]);
            addAssignments(assignments, take(pool, toCount(row.get(0)), byConfidence), 0, SEGMENT_KEYS[i]);
        }
        return assignments;
    }

    private int toCount(Double value) {
        return value == null ? 0 : (int) Math.floor(value + 1e-6);
    }

    private List<ChurnPrediction> take(List<ChurnPrediction> pool, int count, Comparator<ChurnPrediction> priority) {
        if (count <= 0 || pool.isEmpty()) {
            return List.of();
        }
        pool.sort(priority);
        int n = Math.min(count, pool.size());
        List<ChurnPrediction> picked = new ArrayList<>(pool.subList(0, n));
        pool.subList(0, n).clear();
        return picked;
    }

    private void addAssignments(List<UserAssignment> target, List<ChurnPrediction> users,
                                int actionIndex, String riskKey) {
        for (ChurnPrediction u : users) {
            target.add(new UserAssignment(
                    u.getUserId(),
                    u.getName(),
                    u.getEmail(),
                    u.getPhone(),
                    u.getPlan(),
                    riskKey,
                    u.getPredictionConfidence(),
                    ACTION_KEYS[actionIndex],
                    ACTION_LABELS.get(actionIndex)));
        }
    }
}