package com.fitnesslife.gym.service;

import com.fitnesslife.gym.enums.AccessResult;
import com.fitnesslife.gym.model.Attendance;
import com.fitnesslife.gym.model.User;
import com.fitnesslife.gym.repository.AttendanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import com.fitnesslife.gym.exception.AttendanceNotFoundException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final UserService userService;
    private final PaymentService paymentService;
    private final StringRedisTemplate redisTemplate;

    private static final String REDIS_LOCK_PREFIX = "attendance:lock:user:";
    private static final long LOCK_DURATION_SECONDS = 3;

    public enum AttendanceStatus {
        ALLOWED,
        EXIT,
        DENIED,
        NOT_FOUND
    }

    public record AttendanceResult(
            AttendanceStatus status,
            String message,
            String userName,
            String userPhoto,
            String userPlan,
            String userId) {
    }

    @Transactional
    public AttendanceResult processQrScan(String qrCode) {
        log.info("Processing QR scan");

        if (qrCode == null || qrCode.isBlank()) {
            log.warn("Empty QR code received");
            return new AttendanceResult(AttendanceStatus.NOT_FOUND,
                    "Código QR no reconocido", null, null, null, null);
        }

        Optional<User> userOpt = userService.getUserByQrToken(qrCode.trim());
        if (userOpt.isEmpty()) {
            log.warn("No user found for scanned QR token");
            return new AttendanceResult(AttendanceStatus.NOT_FOUND,
                    "Código QR no reconocido", null, null, null, null);
        }

        User user = userOpt.get();
        log.info("User found: {} ({})", user.getName(), user.getId());

        String lockKey = REDIS_LOCK_PREFIX + user.getId();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "locked", LOCK_DURATION_SECONDS, TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(acquired)) {
            log.warn("Duplicate scan blocked for user: {}", user.getId());
            return new AttendanceResult(AttendanceStatus.DENIED,
                    "Escaneo duplicado. Por favor espera un momento.",
                    user.getName() + " " + user.getLastname(),
                    user.getPhotoProfile(), user.getPlan(), user.getId());
        }

        boolean hasActivePlan = paymentService.getActivePayment(user.getId()).isPresent();
        if (!hasActivePlan) {
            log.info("User {} has no active plan — access denied", user.getId());
            saveRecord(user, qrCode, null, null, AccessResult.DENIED);
            return new AttendanceResult(AttendanceStatus.DENIED,
                    "Acceso denegado — sin plan activo",
                    user.getName() + " " + user.getLastname(),
                    user.getPhotoProfile(), user.getPlan(), user.getId());
        }

        Optional<Attendance> active = attendanceRepository.findActiveAttendanceByUserId(user.getId());

        if (active.isPresent()) {
            Attendance att = active.get();
            att.setCheckOut(LocalDateTime.now());
            attendanceRepository.save(att);
            log.info("Check-out registered for user: {}", user.getId());
            return new AttendanceResult(AttendanceStatus.EXIT,
                    "¡Hasta pronto! Gracias por asistir",
                    user.getName() + " " + user.getLastname(),
                    user.getPhotoProfile(), user.getPlan(), user.getId());
        }

        saveRecord(user, qrCode, LocalDateTime.now(), null, AccessResult.ALLOWED);
        log.info("Check-in registered for user: {}", user.getId());
        return new AttendanceResult(AttendanceStatus.ALLOWED,
                "¡Acceso permitido! Bienvenido",
                user.getName() + " " + user.getLastname(),
                user.getPhotoProfile(), user.getPlan(), user.getId());
    }

    public Page<Attendance> getAttendancesPaginated(int page, int size,
            AccessResult result, String search) {

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "checkIn"));

        String safeSearch = SearchTermSanitizer.sanitize(search);
        boolean hasSearch = safeSearch != null && !safeSearch.isBlank();

        if (hasSearch && result != null)
            return attendanceRepository.searchAttendancesByResult(result, safeSearch, pageable);
        if (hasSearch)
            return attendanceRepository.searchAttendances(safeSearch, pageable);
        if (result != null)
            return attendanceRepository.findByResult(result, pageable);

        return attendanceRepository.findAll(pageable);
    }

    public Attendance getAttendanceById(String id) {
        return attendanceRepository.findById(id)
                .orElseThrow(() -> new AttendanceNotFoundException("Registro de asistencia no encontrado: " + id));
    }

    @Transactional
    public Attendance updateAttendanceResult(String id, AccessResult newResult) {
        log.info("Actualizando resultado del acceso {} a {}", id, newResult);
        Attendance attendance = getAttendanceById(id);
        AccessResult oldResult = attendance.getResult();
        attendance.setResult(newResult);
        Attendance updated = attendanceRepository.save(attendance);
        log.info("Resultado actualizado de {} a {}", oldResult, newResult);
        return updated;
    }

    @Transactional
    public void deleteAttendance(String id) {
        attendanceRepository.deleteById(id);
    }

    public int getActiveGymCount() {
        return attendanceRepository.findAllActiveAttendances().size();
    }

    private void saveRecord(User user, String qrCode,
            LocalDateTime checkIn, LocalDateTime checkOut, AccessResult result) {
        attendanceRepository.save(Attendance.builder()
                .user(user)
                .qrCode(qrCode)
                .checkIn(checkIn)
                .checkOut(checkOut)
                .result(result)
                .userName(user.getName() + " " + user.getLastname())
                .userEmail(user.getEmail())
                .userPhoto(user.getPhotoProfile())
                .userPlan(user.getPlan())
                .build());
    }
}