package com.fitnesslife.gym.exception;

import org.springframework.http.HttpStatus;

public class NoActivePlanException extends DomainException {
    public NoActivePlanException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "NO_ACTIVE_PLAN", message);
    }
}