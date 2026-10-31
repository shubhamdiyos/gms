package com.gms.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gms.util.Constants;

/**
 * The single place API errors become a response body.
 *
 * <p>Nothing about the failure internals leaves the process: the previous revision returned the
 * exception class, the root-cause class, both messages and two stack frames, which disclosed SQL
 * fragments, table names and library versions to any caller (finding SEC-11, reproduced in
 * {@code docs/audit/BASELINE_STATUS.md} §4.5). Callers get a stable shape plus a trace id that
 * operators can correlate with the server log, where the full stack still goes.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex,
                                                                HttpServletRequest request) {
        Map<String, Object> response = base(request);
        response.put("error", "Validation failed");
        response.put("message", "One or more fields are invalid.");
        response.put("fields", fieldErrors(ex.getBindingResult().getFieldErrors()));
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Map<String, Object>> handleBind(BindException ex, HttpServletRequest request) {
        Map<String, Object> response = base(request);
        response.put("error", "Invalid request parameters");
        response.put("message", "One or more fields are invalid.");
        response.put("fields", fieldErrors(ex.getBindingResult().getFieldErrors()));
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        Map<String, Object> response = base(request);
        response.put("error", "Not Found");
        response.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * {@code jakarta.persistence.EntityNotFoundException} is what this codebase throws when a
     * referenced record is absent — the service implementations across every module use it that way —
     * but it was falling through to the 500 handler, so a bad foreign-key in a payload read as a
     * server failure.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEntityNotFound(EntityNotFoundException ex,
                                                                    HttpServletRequest request) {
        log.warn("Entity not found [traceId={}] {} {}: {}", traceId(request),
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        Map<String, Object> response = base(request);
        response.put("error", "Not Found");
        response.put("message", "The requested record does not exist.");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Service-layer rejection of a request the caller can correct: a duplicate business key, a null
     * tenant reference, a password below policy. Every throw site is a client-data conflict, so a
     * 500 both misdescribes the failure and hides it from the frontend's error handling.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex,
                                                               HttpServletRequest request) {
        log.warn("Rejected request [traceId={}] {} {}: {}", traceId(request),
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        Map<String, Object> response = base(request);
        response.put("error", "Invalid request");
        response.put("message", ex.getMessage());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex, HttpServletRequest request) {
        String traceId = traceId(request);
        log.error("Unhandled exception [traceId={}] {} {}", traceId,
                request.getMethod(), request.getRequestURI(), ex);
        Map<String, Object> response = base(request);
        response.put("error", "Internal Server Error");
        response.put("message", "The request could not be completed. Report reference " + traceId + ".");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private static Map<String, String> fieldErrors(java.util.List<FieldError> errors) {
        Map<String, String> byField = new LinkedHashMap<>();
        for (FieldError error : errors) {
            byField.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return byField;
    }

    private static Map<String, Object> base(HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("traceId", traceId(request));
        response.put("path", request.getRequestURI());
        return response;
    }

    private static String traceId(HttpServletRequest request) {
        String fromMdc = org.slf4j.MDC.get(Constants.TRACE_ID_KEY);
        return fromMdc != null ? fromMdc : "unknown";
    }
}
