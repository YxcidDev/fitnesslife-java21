package com.fitnesslife.gym.exception;

import org.springframework.http.HttpStatus;

public class InvalidRetentionParametersException extends DomainException {

    public InvalidRetentionParametersException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_RETENTION_PARAMETERS", message);
    }
}
