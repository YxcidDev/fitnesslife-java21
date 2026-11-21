package com.fitnesslife.gym.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import com.fitnesslife.gym.enums.ClassesStatus;
import com.fitnesslife.gym.exception.AlreadyEnrolledException;
import com.fitnesslife.gym.exception.EnrollmentException;
import com.fitnesslife.gym.exception.FunctionalTrainingNotFoundException;
import com.fitnesslife.gym.exception.NoActivePlanException;
import com.fitnesslife.gym.model.FunctionalTraining;
import com.fitnesslife.gym.model.User;
import com.fitnesslife.gym.repository.FunctionalTrainingEnrollmentStore;
import com.fitnesslife.gym.repository.FunctionalTrainingRepository;
import com.fitnesslife.gym.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassEnrollmentService {

    private static final Duration LOCK_TTL = Duration.ofSeconds(15);
    private static final Duration MAX_CLASS_DURATION = Duration.ofMinutes(999);

    private final FunctionalTrainingRepository repository;
    private final FunctionalTrainingEnrollmentStore store;
    private final PaymentRepository paymentRepository;
    private final RedisLockService lockService;

    public FunctionalTraining validateEnroll(User user, int classId, Instant now) {
        FunctionalTraining training = find(classId);
        checkEnrollable(user, training, now);
        return training;
    }

    public FunctionalTraining validateCancel(User user, int classId, Instant now) {
        FunctionalTraining training = find(classId);
        checkCancellable(user, training, now);
        return training;
    }

    public void enroll(User user, int classId) {
        String lockKey = "enroll:" + user.getId();
        String token = lockService.tryAcquire(lockKey, LOCK_TTL).orElseThrow(EnrollmentException::busy);
        try {
            Instant now = Instant.now();
            FunctionalTraining training = find(classId);
            checkEnrollable(user, training, now);

            if (!store.tryAddUser(classId, user.getIdentification(), now)) {
                // Someone else took the last spot (or the class changed) between the check and the write.
                checkEnrollable(user, find(classId), Instant.now());
                throw EnrollmentException.failed();
            }
            log.info("Usuario {} inscrito en la clase {}", user.getId(), classId);
        } finally {
            lockService.release(lockKey, token);
        }
    }

    public void cancel(User user, int classId) {
        String lockKey = "enroll:" + user.getId();
        String token = lockService.tryAcquire(lockKey, LOCK_TTL).orElseThrow(EnrollmentException::busy);
        try {
            Instant now = Instant.now();
            FunctionalTraining training = find(classId);
            checkCancellable(user, training, now);

            if (!store.tryRemoveUser(classId, user.getIdentification(), now)) {
                checkCancellable(user, find(classId), Instant.now());
                throw EnrollmentException.failed();
            }
            log.info("Usuario {} canceló su inscripción en la clase {}", user.getId(), classId);
        } finally {
            lockService.release(lockKey, token);
        }
    }

    private FunctionalTraining find(int classId) {
        return repository.findByIdFunctionalTraining(classId)
                .orElseThrow(() -> new FunctionalTrainingNotFoundException("No encontré esa clase."));
    }

    private void checkEnrollable(User user, FunctionalTraining training, Instant now) {
        if (paymentRepository.findActiveByUserId(user.getId(), LocalDateTime.now()).isEmpty()) {
            throw new NoActivePlanException(
                    "No tienes un plan vigente. Renueva tu membresía en la sección Planes para inscribirte en clases.");
        }
        if (!isOpen(training, now)) {
            throw EnrollmentException.classClosed();
        }
        List<Long> enrolled = enrolled(training);
        if (enrolled.contains(user.getIdentification())) {
            throw new AlreadyEnrolledException("Ya estás inscrito en esta clase.");
        }
        if (enrolled.size() >= training.getMaximumCapacity()) {
            throw EnrollmentException.classFull();
        }
        FunctionalTraining conflict = findConflict(user, training);
        if (conflict != null) {
            throw EnrollmentException.scheduleConflict(conflict.getNameTraining());
        }
    }

    private void checkCancellable(User user, FunctionalTraining training, Instant now) {
        if (training.getDatetime() == null || !training.getDatetime().toInstant().isAfter(now)) {
            throw EnrollmentException.cancelClosed();
        }
        if (!enrolled(training).contains(user.getIdentification())) {
            throw EnrollmentException.notEnrolled();
        }
    }

    private boolean isOpen(FunctionalTraining training, Instant now) {
        return training.getStatus() == ClassesStatus.ACTIVE && training.getDatetime() != null
                && training.getDatetime().toInstant().isAfter(now);
    }

    private List<Long> enrolled(FunctionalTraining training) {
        return training.getUserIds() == null ? List.of() : training.getUserIds();
    }

    private FunctionalTraining findConflict(User user, FunctionalTraining target) {
        Instant start = target.getDatetime().toInstant();
        Instant end = start.plus(Duration.ofMinutes(target.durationMinutes()));
        for (FunctionalTraining other : store.findEnrolledStartingBetween(user.getIdentification(),
                start.minus(MAX_CLASS_DURATION), end)) {
            if (other.getDatetime() == null || other.getIdFunctionalTraining() == target.getIdFunctionalTraining()) {
                continue;
            }
            Instant otherStart = other.getDatetime().toInstant();
            Instant otherEnd = otherStart.plus(Duration.ofMinutes(other.durationMinutes()));
            if (otherStart.isBefore(end) && otherEnd.isAfter(start)) {
                return other;
            }
        }
        return null;
    }
}