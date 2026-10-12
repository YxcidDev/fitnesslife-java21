package com.fitnesslife.gym.exception;

import org.springframework.http.HttpStatus;

public class PaymentAmountMismatchException extends DomainException {
    public PaymentAmountMismatchException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "PAYMENT_AMOUNT_MISMATCH", message);
    }
}