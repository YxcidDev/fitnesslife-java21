package com.fitnesslife.gym.exception;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import com.fitnesslife.gym.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final String ERROR_VIEW = "error/generic-error";

    @ExceptionHandler(DomainException.class)
    public Object handleDomainException(DomainException ex, HttpServletRequest request) {
        HttpStatus status = ex.getStatus();
        String traceId = newTraceId();

        if (status.is5xxServerError()) {
            log.error("[{}] {} {} -> {} ({})", traceId, request.getMethod(), request.getRequestURI(),
                    status.value(), ex.getCode(), ex);
        } else {
            log.warn("[{}] {} {} -> {} ({}): {}", traceId, request.getMethod(), request.getRequestURI(),
                    status.value(), ex.getCode(), ex.getMessage());
        }

        return respond(status, ex.getCode(), ex.getMessage(), null, traceId, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();

        String traceId = newTraceId();
        log.warn("[{}] {} {} -> 422 (VALIDATION_ERROR): {}", traceId, request.getMethod(),
                request.getRequestURI(), violations);

        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                "Los datos enviados no son válidos", violations, traceId, request);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class })
    public Object handleBadRequest(Exception ex, HttpServletRequest request) {
        String traceId = newTraceId();
        logClientError(traceId, HttpStatus.BAD_REQUEST, "BAD_REQUEST", request);

        return respond(HttpStatus.BAD_REQUEST, "BAD_REQUEST",
                "La solicitud es inválida o está incompleta", null, traceId, request);
    }

    @ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class })
    public Object handleNotFound(Exception ex, HttpServletRequest request) {
        String traceId = newTraceId();
        logClientError(traceId, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", request);

        return respond(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                "La página o el recurso que buscas no existe", null, traceId, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        String traceId = newTraceId();
        logClientError(traceId, HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", request);

        return respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "Este recurso no admite esa operación", null, traceId, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleUploadTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        String traceId = newTraceId();
        logClientError(traceId, HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", request);

        return respond(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                "El archivo supera el tamaño máximo permitido (5 MB)", null, traceId, request);
    }

    @ExceptionHandler(Exception.class)
    public Object handleUnexpected(Exception ex, HttpServletRequest request) {
        String traceId = newTraceId();

        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());
            if (status != null && status.is4xxClientError()) {
                logClientError(traceId, status, "REQUEST_REJECTED", request);
                return respond(status, "REQUEST_REJECTED",
                        "La solicitud no pudo ser procesada", null, traceId, request);
            }
        }

        log.error("[{}] Error inesperado en {} {}", traceId, request.getMethod(), request.getRequestURI(), ex);

        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Ha ocurrido un error inesperado", null, traceId, request);
    }

    private Object respond(HttpStatus status, String code, String message,
            List<ApiError.FieldViolation> fieldErrors, String traceId, HttpServletRequest request) {

        if (expectsJson(request)) {
            ApiError body = new ApiError(Instant.now(), status.value(), code, message,
                    request.getRequestURI(), traceId, fieldErrors);
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
        }

        ModelAndView mav = new ModelAndView(ERROR_VIEW);
        mav.setStatus(status);
        mav.addObject("status", status.value());
        mav.addObject("title", titleFor(status.value()));
        mav.addObject("error", message);
        mav.addObject("traceId", traceId);
        mav.addObject("fieldErrors", fieldErrors);
        return mav;
    }

    private boolean expectsJson(HttpServletRequest request) {
        if (request.getRequestURI().startsWith("/api/")) {
            return true;
        }
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept == null || !accept.contains(MediaType.TEXT_HTML_VALUE);
    }

    private String titleFor(int status) {
        return switch (status) {
            case 400 -> "Solicitud inválida";
            case 403 -> "Acceso denegado";
            case 404 -> "No encontrado";
            case 405 -> "Operación no permitida";
            case 409 -> "Conflicto";
            case 413 -> "Archivo demasiado grande";
            case 422 -> "No se pudo procesar la solicitud";
            default -> status >= 500 ? "Algo salió mal" : "Error en la solicitud";
        };
    }

    private void logClientError(String traceId, HttpStatus status, String code, HttpServletRequest request) {
        log.warn("[{}] {} {} -> {} ({})", traceId, request.getMethod(), request.getRequestURI(),
                status.value(), code);
    }

    private String newTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}