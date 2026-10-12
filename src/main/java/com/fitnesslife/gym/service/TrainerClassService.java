package com.fitnesslife.gym.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fitnesslife.gym.dto.TrainerClassDTO;
import com.fitnesslife.gym.enums.ClassPhase;
import com.fitnesslife.gym.enums.TrainerClassFilter;
import com.fitnesslife.gym.model.FunctionalTraining;
import com.fitnesslife.gym.model.User;
import com.fitnesslife.gym.repository.FunctionalTrainingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainerClassService {

    private static final int PAGE_SIZE = 10;
    private static final long ONE_MILLISECOND_IN_NANOS = 1_000_000L;

    private final FunctionalTrainingRepository trainingRepository;
    private final UserService userService;

    public record TrainerClassesResult(
            Page<TrainerClassDTO> classes,
            long classesToday,
            TrainerClassDTO nextClass) {
    }

    public TrainerClassesResult getAssignedClasses(String trainerEmail, TrainerClassFilter filter, int page) {
        User trainer = userService.getUserOrThrow(trainerEmail);
        String trainerId = trainer.getId();
        log.info("Consultando clases asignadas del entrenador {} con filtro {}", trainerId, filter);

        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDateTime now = LocalDateTime.now(zone);

        Page<FunctionalTraining> trainingsPage = findByFilter(trainerId, filter, today, now, page);

        Map<Long, String> attendeeNames = loadAttendeeNames(trainingsPage.getContent());
        Page<TrainerClassDTO> classes = trainingsPage.map(training -> toDto(training, attendeeNames, now));

        long classesToday = trainingRepository.countByInstructorIdAndDatetimeBetween(
                trainerId, inclusiveStart(today.atStartOfDay()), today.plusDays(1).atStartOfDay());

        TrainerClassDTO nextClass = trainingRepository
                .findFirstByInstructorIdAndDatetimeGreaterThanEqualOrderByDatetimeAsc(trainerId, now)
                .map(training -> toDto(training, Collections.emptyMap(), now))
                .orElse(null);

        return new TrainerClassesResult(classes, classesToday, nextClass);
    }

    private Page<FunctionalTraining> findByFilter(String trainerId, TrainerClassFilter filter,
            LocalDate today, LocalDateTime now, int page) {

        Pageable ascending = PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.ASC, "datetime"));
        Pageable descending = PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "datetime"));

        return switch (filter) {
            case TODAY -> findBetween(trainerId, today.atStartOfDay(), today.plusDays(1).atStartOfDay(), ascending);
            case WEEK -> {
                LocalDate monday = today.with(DayOfWeek.MONDAY);
                yield findBetween(trainerId, monday.atStartOfDay(), monday.plusWeeks(1).atStartOfDay(), ascending);
            }
            case MONTH -> {
                LocalDate firstDay = today.withDayOfMonth(1);
                yield findBetween(trainerId, firstDay.atStartOfDay(), firstDay.plusMonths(1).atStartOfDay(),
                        ascending);
            }
            case UPCOMING -> trainingRepository
                    .findByInstructorIdAndDatetimeGreaterThanEqual(trainerId, now, ascending);
            case HISTORY -> trainingRepository
                    .findByInstructorIdAndDatetimeLessThan(trainerId, now, descending);
        };
    }

    private Page<FunctionalTraining> findBetween(String trainerId, LocalDateTime start, LocalDateTime endExclusive,
            Pageable pageable) {
        return trainingRepository.findByInstructorIdAndDatetimeBetween(
                trainerId, inclusiveStart(start), endExclusive, pageable);
    }

    /**
     * Spring Data MongoDB's Between is exclusive on both bounds. Moving the start back one
     * millisecond (the precision of Mongo dates) makes the start instant inclusive.
     */
    private LocalDateTime inclusiveStart(LocalDateTime start) {
        return start.minusNanos(ONE_MILLISECOND_IN_NANOS);
    }

    private Map<Long, String> loadAttendeeNames(List<FunctionalTraining> trainings) {
        Set<Long> identifications = trainings.stream()
                .filter(training -> training.getUserIds() != null)
                .flatMap(training -> training.getUserIds().stream())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (identifications.isEmpty()) {
            return Collections.emptyMap();
        }

        return userService.getUsersByIdentifications(identifications).stream()
                .filter(user -> user.getIdentification() != null)
                .collect(Collectors.toMap(
                        User::getIdentification,
                        user -> user.getName() + " " + user.getLastname(),
                        (first, second) -> first));
    }

    private TrainerClassDTO toDto(FunctionalTraining training, Map<Long, String> namesByIdentification,
            LocalDateTime now) {

        List<Long> enrolledIds = training.getUserIds() == null ? List.of() : training.getUserIds();
        List<String> attendeeNames = enrolledIds.stream()
                .filter(Objects::nonNull)
                .map(namesByIdentification::get)
                .filter(Objects::nonNull)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        int capacity = training.getMaximumCapacity();
        int occupancyPercent = capacity > 0 ? Math.min(100, enrolledIds.size() * 100 / capacity) : 0;

        return TrainerClassDTO.builder()
                .id(training.getId())
                .name(training.getNameTraining())
                .room(training.getRoom())
                .startsAt(training.startsAt())
                .endsAt(training.endsAt())
                .durationMinutes(training.durationMinutes())
                .enrolledCount(enrolledIds.size())
                .capacity(capacity)
                .occupancyPercent(occupancyPercent)
                .phase(phaseOf(training, now))
                .attendeeNames(attendeeNames)
                .build();
    }

    private ClassPhase phaseOf(FunctionalTraining training, LocalDateTime now) {
        if (now.isBefore(training.startsAt())) {
            return ClassPhase.UPCOMING;
        }
        if (now.isBefore(training.endsAt())) {
            return ClassPhase.IN_PROGRESS;
        }
        return ClassPhase.FINISHED;
    }
}