package com.fitnesslife.gym.exception;

import org.springframework.http.HttpStatus;

public class InvalidProfileImageException extends DomainException {
    public InvalidProfileImageException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PROFILE_IMAGE", message);
    }
}