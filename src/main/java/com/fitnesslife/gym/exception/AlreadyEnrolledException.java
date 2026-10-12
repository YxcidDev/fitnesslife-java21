package com.fitnesslife.gym.exception;

import org.springframework.http.HttpStatus;

public class AlreadyEnrolledException extends DomainException {
    public AlreadyEnrolledException(String message) {
        super(HttpStatus.CONFLICT, "ALREADY_ENROLLED", message);
    }
}