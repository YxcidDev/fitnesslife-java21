package com.fitnesslife.gym.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.fitnesslife.gym.service.PendingActionService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PendingActionScheduler {

    private final PendingActionService service;

    @Scheduled(fixedDelay = 60_000)
    public void expireStale() {
        service.expireStale();
    }
}