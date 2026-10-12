package com.fitnesslife.gym.exception;

import org.springframework.http.HttpStatus;

public class AttendanceNotFoundException extends DomainException {
    public AttendanceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "ATTENDANCE_NOT_FOUND", message);
    }
}