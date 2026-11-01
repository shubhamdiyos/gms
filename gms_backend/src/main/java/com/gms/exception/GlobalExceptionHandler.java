package com.gms.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    /**
     * A method-level authorisation refusal.
     *
     * <p>{@code @PreAuthorize} denials arrive here as {@code AuthorizationDeniedException}, which
     * extends {@code AccessDeniedException}. Without this handler they fell through to the
     * catch-all below and answered <b>500</b>: every guarded endpoint told the caller that the
     * server had broken, when the truth is that the caller was not allowed. That is a false 5xx
     * on the most common rejection the API produces, and it hides refusals from the frontend's
     * error handling.</p>
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex,
                                                                  HttpServletRequest request) {
        log.warn("Refused [traceId={}] {} {}: {}", traceId(request),
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        Map<String, Object> response = base(request);
        response.put("error", "Forbidden");
        response.put("message", "You are not allowed to perform this action.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * A path variable or query parameter that is not the type the handler declares.
     *
     * <p>{@code GET /api/v1/subjects/update/} — an id that is not an integer — reached the catch-all
     * and answered <b>500</b>. It is a malformed request, and it is one a caller makes by
     * interpolation rather than by intent.</p>
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                                 HttpServletRequest request) {
        log.warn("Type mismatch [traceId={}] {} {}", traceId(request),
                request.getMethod(), request.getRequestURI());
        Map<String, Object> response = base(request);
        response.put("error", "Invalid request");
        response.put("message", "A value in the request is not valid.");
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

    /**
     * The path exists but the method does not.
     *
     * <p>Stage 3's C-1 removed the inherited {@code DELETE /{id}} routes, and the paths they used to
     * occupy still answer {@code GET}. Without this handler the mismatch reached the catch-all
     * above and answered <b>500</b>, which told the caller the server had failed when the request
     * was simply wrong. Measured live on 2026-11-01: {@code DELETE /api/v1/students/1},
     * {@code DELETE /api/v1/employees/1} and {@code DELETE /api/v1/schools/1} each answered 500
     * with {@code HttpRequestMethodNotSupportedException} in the log.</p>
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                       HttpServletRequest request) {
        log.warn("Method not supported [traceId={}] {} {}", traceId(request),
                request.getMethod(), request.getRequestURI());
        Map<String, Object> response = base(request);
        response.put("error", "Method Not Allowed");
        response.put("message", "That operation is not available on this resource.");
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }

    /**
     * The row is still referenced by another one, so the database refuses the delete.
     *
     * <p>Deleting a classroom that still has students violates the {@code students.classroom_id}
     * foreign key. The database's answer was reaching the catch-all as a <b>500</b>, which reads
     * as a server fault; it is a conflict between two rows the caller can resolve. Measured live on
     * 2026-11-01: {@code DELETE /api/v1/classrooms/1} answered 500 for a classroom with one
     * student.</p>
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(DataIntegrityViolationException ex,
                                                              HttpServletRequest request) {
        log.warn("Conflict [traceId={}] {} {}: {}", traceId(request),
                request.getMethod(), request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        Map<String, Object> response = base(request);
        response.put("error", "Conflict");
        response.put("message", "That record is still referenced by other records and cannot be deleted.");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
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
