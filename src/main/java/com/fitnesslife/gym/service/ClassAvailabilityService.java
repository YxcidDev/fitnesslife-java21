package com.fitnesslife.gym.service;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import com.fitnesslife.gym.config.ChatbotProperties;
import com.fitnesslife.gym.enums.ClassesStatus;
import com.fitnesslife.gym.model.FunctionalTraining;
import com.fitnesslife.gym.repository.FunctionalTrainingRepository;
import com.fitnesslife.gym.tool.ToolInputException;
import com.fitnesslife.gym.tool.ToolRunner.ToolResult;
import com.fitnesslife.gym.tool.ToolSession;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassAvailabilityService {

    public record ClassInfo(int classId, String name, String instructor, String startsAt, String endsAt,
                            int durationMinutes, String room, int capacity, int enrolled, int freeSpots, String state,
                            boolean alreadyEnrolled) {
    }

    public record Availability(String date, String dayOfWeek, String todayIs, int totalClasses, boolean truncated,
                               List<ClassInfo> classes) {
    }

    private static final Locale SPANISH = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final long ONE_MILLISECOND_IN_NANOS = 1_000_000L;
    private static final int MAX_NAME_LENGTH = 60;

    private final FunctionalTrainingRepository repository;
    private final ChatbotProperties properties;

    private ZoneId gymZone;

    @PostConstruct
    void init() {
        gymZone = ZoneId.of(properties.tools().timezone());
        if (!ZoneId.systemDefault().getRules().equals(gymZone.getRules())) {
            log.warn("La zona horaria de la JVM ({}) difiere de la del gimnasio ({}). Las clases se guardan con la "
                            + "zona de la JVM: arranca la aplicación con -Duser.timezone={}", ZoneId.systemDefault(),
                    gymZone, gymZone);
        }
    }

    public ToolResult availability(ToolSession session, String dateText, String classNameFilter) {
        LocalDate today = LocalDate.now(gymZone);
        LocalDate date = resolveDate(dateText, today);
        String filter = normalize(classNameFilter);
        if (filter.length() > MAX_NAME_LENGTH) {
            throw new ToolInputException("El nombre de la clase es demasiado largo.");
        }

        LocalDateTime from = toSystemLocal(date.atStartOfDay(gymZone)).minusNanos(ONE_MILLISECOND_IN_NANOS);
        LocalDateTime to = toSystemLocal(date.plusDays(1).atStartOfDay(gymZone));
        List<FunctionalTraining> matches = repository.findByDatetimeBetweenOrderByDatetimeAsc(from, to).stream()
                .filter(training -> training.getDatetime() != null)
                .filter(training -> filter.isEmpty() || normalize(training.getNameTraining()).contains(filter))
                .toList();

        int limit = properties.tools().maxClassesPerCall();
        Instant now = Instant.now();
        List<ClassInfo> classes = matches.stream().limit(limit).map(t -> toInfo(t, session, now)).toList();

        Availability result = new Availability(date.toString(), date.getDayOfWeek().getDisplayName(TextStyle.FULL, SPANISH),
                today.toString(), matches.size(), matches.size() > limit, classes);
        return new ToolResult(result, matches.size() + " clases");
    }

    private ClassInfo toInfo(FunctionalTraining training, ToolSession session, Instant now) {
        Instant start = training.getDatetime().toInstant();
        Instant end = start.plus(Duration.ofMinutes(training.durationMinutes()));
        int enrolled = training.getUserIds() == null ? 0 : training.getUserIds().size();

        String state;
        if (now.isBefore(start)) {
            state = training.getStatus() == ClassesStatus.INACTIVE ? "CLOSED" : "UPCOMING";
        } else {
            state = now.isBefore(end) ? "IN_PROGRESS" : "FINISHED";
        }
        boolean mine = session.identification() != null && training.getUserIds() != null
                && training.getUserIds().contains(session.identification());

        return new ClassInfo(training.getIdFunctionalTraining(), training.getNameTraining(), training.getInstructor(),
                ZonedDateTime.ofInstant(start, gymZone).format(HOUR), ZonedDateTime.ofInstant(end, gymZone).format(HOUR),
                training.durationMinutes(), training.getRoom(), training.getMaximumCapacity(), enrolled,
                Math.max(0, training.getMaximumCapacity() - enrolled), state, mine);
    }

    private LocalDate resolveDate(String text, LocalDate today) {
        LocalDate date;
        String value = normalize(text);
        if (value.isEmpty() || value.equals("hoy") || value.equals("today")) {
            date = today;
        } else if (value.equals("manana") || value.equals("tomorrow")) {
            date = today.plusDays(1);
        } else {
            try {
                date = LocalDate.parse(text.strip());
            } catch (DateTimeParseException e) {
                throw new ToolInputException("La fecha debe tener el formato yyyy-MM-dd, o ser «hoy» o «mañana».");
            }
        }
        int maxDays = properties.tools().maxDaysAhead();
        if (date.isBefore(today)) {
            throw new ToolInputException("Solo se pueden consultar clases de hoy en adelante.");
        }
        if (date.isAfter(today.plusDays(maxDays))) {
            throw new ToolInputException("Solo se pueden consultar los próximos " + maxDays + " días.");
        }
        return date;
    }

    private LocalDateTime toSystemLocal(ZonedDateTime moment) {
        return LocalDateTime.ofInstant(moment.toInstant(), ZoneId.systemDefault());
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).strip();
    }
}