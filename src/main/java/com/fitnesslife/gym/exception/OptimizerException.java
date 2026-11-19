package com.fitnesslife.gym.exception;

import org.springframework.http.HttpStatus;

public class OptimizerException extends DomainException {

    public OptimizerException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static OptimizerException unavailable() {
        return new OptimizerException(HttpStatus.SERVICE_UNAVAILABLE, "OPTIMIZER_UNAVAILABLE",
                "El servicio de optimización no está disponible. Verifica que el microservicio esté en ejecución.");
    }

    public static OptimizerException badResponse(int httpStatus) {
        return new OptimizerException(HttpStatus.BAD_GATEWAY, "OPTIMIZER_ERROR",
                "El servicio de optimización respondió con un error (HTTP " + httpStatus + ").");
    }

    public static OptimizerException emptyResponse() {
        return new OptimizerException(HttpStatus.BAD_GATEWAY, "OPTIMIZER_EMPTY_RESPONSE",
                "El servicio de optimización devolvió una respuesta vacía.");
    }
}
