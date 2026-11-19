package com.fitnesslife.gym.exception;

public class RetentionMailException extends RuntimeException {

    private final boolean retryable;

    public RetentionMailException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public RetentionMailException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}