package com.fitnesslife.gym.exception;

import org.springframework.http.HttpStatus;

public class PaymentNotFoundException extends DomainException {
    public PaymentNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", message);
    }
}