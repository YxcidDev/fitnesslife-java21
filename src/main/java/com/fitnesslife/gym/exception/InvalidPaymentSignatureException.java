package com.fitnesslife.gym.exception;

import org.springframework.http.HttpStatus;

public class InvalidPaymentSignatureException extends DomainException {
    public InvalidPaymentSignatureException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_SIGNATURE", message);
    }
}