package com.fitnesslife.gym.service;

import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final String ATTEMPTS_PREFIX = "login:attempts:";
    private static final String LOCKOUT_PREFIX = "login:lockout:";
    private static final int MAX_ATTEMPTS = 5;
    private static final int ATTEMPTS_WINDOW_MIN = 15;
    private static final int LOCKOUT_DURATION_MIN = 15;

    private final StringRedisTemplate redisTemplate;

    public boolean isLocked(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(LOCKOUT_PREFIX + normalize(email)));
    }

    public void recordFailure(String email) {
        if (email == null || email.isBlank()) {
            return;
        }

        String attemptsKey = ATTEMPTS_PREFIX + normalize(email);
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(attemptsKey, ATTEMPTS_WINDOW_MIN, TimeUnit.MINUTES);
        }

        if (attempts != null && attempts >= MAX_ATTEMPTS) {
            redisTemplate.opsForValue().set(LOCKOUT_PREFIX + normalize(email), "1",
                    LOCKOUT_DURATION_MIN, TimeUnit.MINUTES);
            redisTemplate.delete(attemptsKey);
            log.warn("Cuenta bloqueada temporalmente tras {} intentos fallidos: {}", MAX_ATTEMPTS, normalize(email));
        }
    }

    public void recordSuccess(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        redisTemplate.delete(ATTEMPTS_PREFIX + normalize(email));
        redisTemplate.delete(LOCKOUT_PREFIX + normalize(email));
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }
}