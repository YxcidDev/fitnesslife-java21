package com.fitnesslife.gym.service;

import com.fitnesslife.gym.enums.ClassesStatus;
import com.fitnesslife.gym.enums.Role;
import com.fitnesslife.gym.exception.AlreadyEnrolledException;
import com.fitnesslife.gym.exception.FunctionalTrainingNotFoundException;
import com.fitnesslife.gym.exception.InvalidRequestException;
import com.fitnesslife.gym.exception.NoActivePlanException;
import com.fitnesslife.gym.exception.TrainerScheduleConflictException;
import com.fitnesslife.gym.model.FunctionalTraining;
import com.fitnesslife.gym.model.User;
import com.fitnesslife.gym.repository.FunctionalTrainingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class FunctionalTrainingService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final FunctionalTrainingRepository repository;
    private final UserService userService;
    private final PaymentService paymentService;

    public record FilteredTrainingsResult(
            List<FunctionalTraining> todayTrainings,
            Page<FunctionalTraining> trainings) {
    }

    public record InstructorBackfillResult(int linked, int unresolved) {
    }

    public List<FunctionalTraining> getAllTrainings() {
        log.info("Obteniendo todas las clases funcionales");
        return repository.findAll();
    }

    public Optional<FunctionalTraining> getTrainingById(String id) {
        log.info("Buscando clase con ID: {}", id);
        return repository.findById(id);
    }

    public Optional<FunctionalTraining> getTrainingByFunctionalId(int idFunctionalTraining) {
        log.info("Buscando clase con ID funcional: {}", idFunctionalTraining);
        return repository.findByIdFunctionalTraining(idFunctionalTraining);
    }

    @Transactional
    public FunctionalTraining createTraining(FunctionalTraining training) {
        log.info("Creando nueva clase funcional: {}", training.getNameTraining());

        assignInstructor(training);
        assertTrainerAvailable(training, null);

        long count = repository.count();
        training.setIdFunctionalTraining((int) count + 1);
        training.setStatus(ClassesStatus.ACTIVE);

        FunctionalTraining saved = repository.save(training);
        log.info("Clase funcional creada con ID: {}", saved.getId());
        return saved;
    }

    @Transactional
    public FunctionalTraining updateTraining(String id, FunctionalTraining updatedTraining) {
        log.info("Actualizando clase con ID: {}", id);

        FunctionalTraining training = repository.findById(id)
                .orElseThrow(() -> new FunctionalTrainingNotFoundException("Clase no encontrada: " + id));

        assignInstructor(updatedTraining);
        assertTrainerAvailable(updatedTraining, id);

        training.setNameTraining(updatedTraining.getNameTraining());
        training.setInstructor(updatedTraining.getInstructor());
        training.setInstructorId(updatedTraining.getInstructorId());
        training.setDescription(updatedTraining.getDescription());
        training.setMaximumCapacity(updatedTraining.getMaximumCapacity());
        training.setDuration(updatedTraining.getDuration());
        training.setStatus(updatedTraining.getStatus());
        training.setDatetime(updatedTraining.getDatetime());
        training.setRoom(updatedTraining.getRoom());

        FunctionalTraining saved = repository.save(training);
        log.info("Clase actualizada exitosamente: {}", saved.getNameTraining());
        return saved;
    }

    @Transactional
    public void deleteTraining(String id) {
        log.info("Eliminando clase con ID: {}", id);
        repository.deleteById(id);
        log.info("Clase eliminada exitosamente");
    }

    @Transactional
    public void inscribirUsuario(int idFunctionalTraining, String email) {
        log.info("Inscribiendo al usuario {} en la clase {}", email, idFunctionalTraining);

        User user = userService.getUserOrThrow(email);

        boolean hasActivePlan = paymentService.getActivePayment(user.getId()).isPresent();
        if (!hasActivePlan) {
            throw new NoActivePlanException("El usuario no tiene un plan activo");
        }

        FunctionalTraining training = repository.findByIdFunctionalTraining(idFunctionalTraining)
                .orElseThrow(() -> new FunctionalTrainingNotFoundException(
                        "Clase no encontrada: " + idFunctionalTraining));

        if (training.getUserIds().contains(user.getIdentification())) {
            throw new AlreadyEnrolledException("El usuario ya está inscrito en esta clase");
        }

        training.getUserIds().add(user.getIdentification());
        repository.save(training);
        log.info("Inscripción realizada exitosamente");
    }

    @Transactional
    public void cancelarInscripcion(int idFunctionalTraining, String emailUsuario) {
        log.info("Cancelando inscripción del usuario {} en clase {}", emailUsuario, idFunctionalTraining);

        User user = userService.getUserOrThrow(emailUsuario);

        FunctionalTraining training = repository.findByIdFunctionalTraining(idFunctionalTraining)
                .orElseThrow(() -> new FunctionalTrainingNotFoundException(
                        "Clase no encontrada: " + idFunctionalTraining));

        if (training.getUserIds() != null && training.getUserIds().contains(user.getIdentification())) {
            training.getUserIds().remove(user.getIdentification());
            repository.save(training);
            log.info("Inscripción cancelada exitosamente");
        }
    }

    public FunctionalTraining findByIdFunctional(int idFunctional) {
        log.info("Buscando clase funcional con ID: {}", idFunctional);
        return repository.findByIdFunctionalTraining(idFunctional).orElse(null);
    }

    public List<FunctionalTraining> getTrainingsByUser(String emailUsuario) {
        log.info("Obteniendo clases del usuario: {}", emailUsuario);

        User user = userService.getUserOrThrow(emailUsuario);

        List<FunctionalTraining> allTrainings = repository.findAll();

        return allTrainings.stream()
                .filter(training -> training.getUserIds() != null &&
                        training.getUserIds().contains(user.getIdentification()))
                .toList();
    }

    public List<FunctionalTraining> getTodayTrainings() {
        LocalDate hoy = LocalDate.now(ZoneId.systemDefault());
        return repository.findByDatetimeBetweenOrderByDatetimeAsc(
                hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay());
    }

    public List<FunctionalTraining> getUserTodayTrainings(Long identification) {
        return getTodayTrainings().stream()
                .filter(t -> t.getUserIds() != null && t.getUserIds().contains(identification))
                .toList();
    }

    public FilteredTrainingsResult getFilteredTrainings(String filtro, int page, int size) {
        log.info("Obteniendo clases filtradas - filtro: {}, page: {}, size: {}", filtro, page, size);

        LocalDate hoy = LocalDate.now(ZoneId.systemDefault());
        LocalDateTime startOfToday = hoy.atStartOfDay();
        LocalDateTime endOfTodayExclusive = hoy.plusDays(1).atStartOfDay();

        List<FunctionalTraining> clasesDeHoy = getTodayTrainings();

        Page<FunctionalTraining> trainingsPage = switch (filtro) {
            case "todas" -> repository.findAll(
                    PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "datetime")));

            case "hoy" -> repository.findByDatetimeBetweenOrderByDatetimeAsc(
                    startOfToday, endOfTodayExclusive, PageRequest.of(page, size));

            case "semana" -> {
                LocalDate lunes = hoy.with(java.time.DayOfWeek.MONDAY);
                LocalDate lunesSiguiente = lunes.plusWeeks(1);
                yield repository.findByDatetimeBetweenOrderByDatetimeAsc(
                        lunes.atStartOfDay(), lunesSiguiente.atStartOfDay(), PageRequest.of(page, size));
            }

            default -> {
                LocalDate primerDiaMes = hoy.withDayOfMonth(1);
                LocalDate primerDiaMesSiguiente = primerDiaMes.plusMonths(1);
                yield repository.findByDatetimeBetweenOrderByDatetimeAsc(
                        primerDiaMes.atStartOfDay(), primerDiaMesSiguiente.atStartOfDay(),
                        PageRequest.of(page, size));
            }
        };

        return new FilteredTrainingsResult(clasesDeHoy, trainingsPage);
    }

    @Transactional
    public void actualizarEstados() {
        List<FunctionalTraining> clases = repository.findAll();
        LocalDateTime ahora = LocalDateTime.now();

        clases.forEach(c -> {
            LocalDateTime fechaClase = c.getDatetime()
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();

            if (fechaClase.isBefore(ahora) && c.getStatus() != ClassesStatus.INACTIVE) {
                c.setStatus(ClassesStatus.INACTIVE);
                repository.save(c);
            }
        });
    }

    /**
     * One-time migration helper: links classes that only have the trainer's name to the trainer's
     * user id. A class is linked only when exactly one TRAINER user has that full name.
     * Idempotent: classes that already have an instructorId are never touched.
     */
    @Transactional
    public InstructorBackfillResult backfillInstructorIds() {
        List<FunctionalTraining> pending = repository.findByInstructorIdIsNull();
        if (pending.isEmpty()) {
            return new InstructorBackfillResult(0, 0);
        }

        Map<String, List<User>> trainersByName = userService.getUsersByRole(Role.TRAINER).stream()
                .collect(Collectors.groupingBy(
                        trainer -> normalizeName(trainer.getName() + " " + trainer.getLastname())));

        int linked = 0;
        int unresolved = 0;
        for (FunctionalTraining training : pending) {
            List<User> matches = trainersByName.getOrDefault(normalizeName(training.getInstructor()), List.of());
            if (matches.size() == 1) {
                training.setInstructorId(matches.get(0).getId());
                repository.save(training);
                linked++;
            } else {
                unresolved++;
                log.warn("No se pudo vincular la clase {} (instructor '{}'): {} entrenadores coinciden",
                        training.getId(), training.getInstructor(), matches.size());
            }
        }
        return new InstructorBackfillResult(linked, unresolved);
    }

    /**
     * Resolves the trainer from the submitted instructorId and writes the display name.
     * The server is the only source of the name: whatever the client sends in "instructor" is ignored.
     */
    private void assignInstructor(FunctionalTraining training) {
        String instructorId = training.getInstructorId();
        if (instructorId == null || instructorId.isBlank()) {
            throw new InvalidRequestException("El instructor es obligatorio");
        }

        User trainer = userService.findById(instructorId)
                .orElseThrow(() -> new InvalidRequestException("El entrenador seleccionado no existe"));

        if (trainer.getRole() != Role.TRAINER || !trainer.isActive()) {
            throw new InvalidRequestException("El usuario seleccionado no es un entrenador activo");
        }

        training.setInstructor(trainer.getName() + " " + trainer.getLastname());
    }

    /**
     * Rejects the candidate when its trainer already has another class overlapping
     * [start, end). Back-to-back classes (one ends exactly when the next starts) are allowed.
     */
    private void assertTrainerAvailable(FunctionalTraining candidate, String ignoredTrainingId) {
        LocalDateTime start = candidate.startsAt();
        if (start == null) {
            return;
        }
        LocalDateTime end = candidate.endsAt();

        // Any class overlapping [start, end) started after (start - 1 day): input durations are capped at 999 minutes.
        List<FunctionalTraining> nearbyClasses = repository.findByInstructorIdAndDatetimeBetween(
                candidate.getInstructorId(), start.minusDays(1), end);

        nearbyClasses.stream()
                .filter(other -> ignoredTrainingId == null || !ignoredTrainingId.equals(other.getId()))
                .filter(other -> other.startsAt() != null)
                .filter(other -> other.startsAt().isBefore(end) && other.endsAt().isAfter(start))
                .min(Comparator.comparing(FunctionalTraining::startsAt))
                .ifPresent(busyWith -> {
                    log.warn("Conflicto de horario para el entrenador {}: choca con la clase {} ({})",
                            candidate.getInstructorId(), busyWith.getNameTraining(), busyWith.getId());
                    throw new TrainerScheduleConflictException(
                            buildBusyMessage(candidate.getInstructor(), busyWith));
                });
    }

    private String buildBusyMessage(String instructor, FunctionalTraining busyWith) {
        LocalDateTime busyStart = busyWith.startsAt();
        LocalDateTime busyEnd = busyWith.endsAt();
        return String.format(
                "El entrenador %s está ocupado con la clase \"%s\" el %s, de %s a %s. "
                        + "Estará disponible a partir de las %s.",
                instructor, busyWith.getNameTraining(), DATE_FORMAT.format(busyStart),
                TIME_FORMAT.format(busyStart), TIME_FORMAT.format(busyEnd), TIME_FORMAT.format(busyEnd));
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}