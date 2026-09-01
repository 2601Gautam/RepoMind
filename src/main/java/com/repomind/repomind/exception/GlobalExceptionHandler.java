package com.repomind.repomind.exception;


import kotlin.io.AccessDeniedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

// @RestControllerAdvice: applies to all @RestController classes
// Centralizes exception handling — follows DRY (do not repeat yourself) principle
// Every controller removes its try-catch blocks and becomes cleaner
// All error responses follow the same JSON structure
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Consistent error response structure
    // All errors return this shape: {error, message, timestamp}
    // Frontend only needs to handle one error format
    private Map<String, Object> errorResponse(String error, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", error);
        body.put("message", message);
        body.put("timestamp", LocalDateTime.now().toString());
        return body;
    }

    // Rate limit exceeded - 429 Too many requests
    // Includes Retry-After header so frontend can show countdown
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(RateLimitException ex) {
        Map<String, Object> body = errorResponse("RATE_LIMIT_EXCEEDED", ex.getMessage());
        body.put("retryAfterSeconds", ex.getRetryAfterSeconds());

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(body);
    }

    // Validation failures - 400 Bad Request
    // Returns field-level errors so frontend can highlight the specific field
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            fieldErrors.put(fieldName, message);
        });

        Map<String, Object> body = errorResponse("VALIDATION_FAILED", "Request validation failed");
        body.put("fieldErrors", fieldErrors);

        return ResponseEntity.badRequest().body(body);
    }

    // Acess denied - 403 Forbidden
    // Happens when authenticated user tries to access another user's resource
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(errorResponse("ACCESS_DENIED", "You do not have permission to access this resource"));
    }

    // Business logic errors - 400 Bad Request
    // "Repository not found", "Repo not ready", etc.
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex) {
        log.warn("Business error: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(errorResponse("REQUEST_ERROR", ex.getMessage()));
    }

    // Unexpected errors — 500 Internal Server Error
    // Never expose internal details to the client — log them server-side
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.internalServerError()
                .body(errorResponse("INTERNAL_ERROR", "An unexpected error occurred. Please try again."));
    }

}
