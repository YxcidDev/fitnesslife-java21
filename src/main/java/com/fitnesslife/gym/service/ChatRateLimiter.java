package com.fitnesslife.gym.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.fitnesslife.gym.config.ChatbotProperties;
import com.fitnesslife.gym.exception.ChatException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRateLimiter {

    private final StringRedisTemplate redis;
    private final ChatbotProperties properties;

    public void check(String userId) {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        long epochMinute = now.atZone(ZoneId.systemDefault()).toEpochSecond() / 60;
        int secondsToNextMinute = 60 - now.getSecond();
        long secondsToMidnight = Duration.between(now, LocalDate.now().plusDays(1).atStartOfDay()).getSeconds();

        consume("chat:rate:min:" + userId + ":" + epochMinute, 90,
                properties.limits().messagesPerMinute(), secondsToNextMinute, userId);
        consume("chat:rate:day:" + userId + ":" + LocalDate.now(), 90_000,
                properties.limits().messagesPerDay(), (int) secondsToMidnight, userId);
    }

    private void consume(String key, long ttlSeconds, int limit, int retryAfterSeconds, String userId) {
        Long count = redis.opsForValue().increment(key);
        redis.expire(key, Duration.ofSeconds(ttlSeconds));
        if (count != null && count > limit) {
            log.warn("Límite de mensajes del chat superado por el usuario {}", userId);
            throw ChatException.rateLimited(Math.max(retryAfterSeconds, 1));
        }
    }
}