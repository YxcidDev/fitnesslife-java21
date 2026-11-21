package com.fitnesslife.gym.exception;

import org.springframework.http.HttpStatus;

public class EnrollmentException extends DomainException {

    private EnrollmentException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static EnrollmentException classFull() {
        return new EnrollmentException(HttpStatus.CONFLICT, "CLASS_FULL", "La clase ya no tiene cupos disponibles.");
    }

    public static EnrollmentException classClosed() {
        return new EnrollmentException(HttpStatus.CONFLICT, "CLASS_CLOSED",
                "La clase ya comenzó o no está abierta para inscripciones.");
    }

    public static EnrollmentException cancelClosed() {
        return new EnrollmentException(HttpStatus.CONFLICT, "CLASS_STARTED",
                "La clase ya comenzó: ya no se puede cancelar la inscripción desde el sistema.");
    }

    public static EnrollmentException scheduleConflict(String otherClass) {
        return new EnrollmentException(HttpStatus.CONFLICT, "SCHEDULE_CONFLICT",
                "Ya estás inscrito en «" + otherClass + "», que se cruza con el horario de esta clase.");
    }

    public static EnrollmentException notEnrolled() {
        return new EnrollmentException(HttpStatus.CONFLICT, "NOT_ENROLLED", "No estás inscrito en esta clase.");
    }

    public static EnrollmentException busy() {
        return new EnrollmentException(HttpStatus.CONFLICT, "ENROLLMENT_BUSY",
                "Hay otra operación en curso en tu cuenta. Inténtalo de nuevo en unos segundos.");
    }

    public static EnrollmentException failed() {
        return new EnrollmentException(HttpStatus.CONFLICT, "ENROLLMENT_FAILED",
                "No se pudo completar la operación. Inténtalo de nuevo.");
    }
}