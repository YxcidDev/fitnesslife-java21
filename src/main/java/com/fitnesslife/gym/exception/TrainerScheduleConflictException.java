package com.fitnesslife.gym.exception;

import org.springframework.http.HttpStatus;

public class TrainerScheduleConflictException extends DomainException {
    public TrainerScheduleConflictException(String message) {
        super(HttpStatus.CONFLICT, "TRAINER_SCHEDULE_CONFLICT", message);
    }
}