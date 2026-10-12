package com.fitnesslife.gym.exception;

import org.springframework.http.HttpStatus;

public class IdentificationAlreadyExistsException extends DomainException {
    public IdentificationAlreadyExistsException(String message) {
        super(HttpStatus.CONFLICT, "IDENTIFICATION_ALREADY_EXISTS", message);
    }
}