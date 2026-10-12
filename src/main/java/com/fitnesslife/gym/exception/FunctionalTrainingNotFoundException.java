package com.fitnesslife.gym.exception;

import org.springframework.http.HttpStatus;

public class FunctionalTrainingNotFoundException extends DomainException {
    public FunctionalTrainingNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "TRAINING_NOT_FOUND", message);
    }
}