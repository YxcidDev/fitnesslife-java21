package com.fitnesslife.gym.exception;

import org.springframework.http.HttpStatus;

public class EmailAlreadyExistsException extends DomainException {
    public EmailAlreadyExistsException(String message) {
        super(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", message);
    }
}