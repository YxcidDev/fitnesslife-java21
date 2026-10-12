package com.fitnesslife.gym.exception;

import org.springframework.http.HttpStatus;

public class PlanNotFoundException extends DomainException {
    public PlanNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", message);
    }
}