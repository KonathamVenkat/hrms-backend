package com.hrms.common.exception;

import com.hrms.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised exception handler for all HRMS microservices.
 *
 * <p>This class is placed in {@code common-lib} so each microservice
 * inherits the same error response structure automatically — ensuring
 * the Angular frontend receives a consistent error envelope regardless
 * of which service generated the error.</p>
 *
 * <p>All responses follow the {@link ApiResponse} envelope:
 * <pre>{@code
 * {
 *   "success": false,
 *   "message": "Validation failed",
 *   "statusCode": 400,
 *   "timestamp": "2025-01-15T10:30:00",
 *   "errors": { "fieldName": "error message" }
 * }
 * }</pre>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ──────────────────────────────────────────────────────────────────────────
    // 400 Bad Request
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Handles {@code @Valid} / {@code @Validated} bean validation failures.
     * Returns a map of { fieldName → errorMessage } for the Angular form layer.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = error instanceof FieldError fe ? fe.getField() : error.getObjectName();
            fieldErrors.put(field, error.getDefaultMessage());
        });

        log.warn("Validation failed on [{}] - Fields: {}", request.getRequestURI(), fieldErrors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, "Validation failed. Please check the submitted fields.", fieldErrors));
    }

    /**
     * Handles constraint violations raised on method parameters (e.g., path variables, query params).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        List<String> violations = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toList());

        log.warn("Constraint violation on [{}]: {}", request.getRequestURI(), violations);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, "Constraint violation", violations));
    }

    /**
     * Handles malformed JSON, unreadable request body, or invalid enum values in JSON.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        String message = extractEnumErrorMessage(ex);
        log.warn("Unreadable request on [{}]: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, message));
    }

    /**
     * Handles missing required request parameters.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParams(
            MissingServletRequestParameterException ex) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400,
                        "Required parameter '" + ex.getParameterName() + "' is missing"));
    }

    /**
     * Handles type mismatch for method arguments (e.g., string passed for numeric path variable).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {

        String message = String.format(
                "Parameter '%s' with value '%s' could not be converted to type '%s'",
                ex.getName(), ex.getValue(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown"
        );
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, message));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, ex.getMessage()));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 401 Unauthorized
    // ──────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Authentication failure: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(401, "Invalid username or password"));
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidToken(InvalidTokenException ex) {
        log.warn("Invalid token: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(401, ex.getMessage()));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 403 Forbidden
    // ──────────────────────────────────────────────────────────────────────────

    /** Locked or disabled accounts: the message is written for the user ("Contact HR", "try again later"). */
    @ExceptionHandler({AccountLockedException.class, AccountDisabledException.class})
    public ResponseEntity<ApiResponse<Void>> handleAccountState(RuntimeException ex) {
        log.warn("Sign-in refused: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(403, ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {

        log.warn("Access denied to [{}]: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(403, "You do not have permission to perform this action"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 404 Not Found
    // ──────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, ex.getMessage()));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 409 Conflict
    // ──────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateResourceException ex) {
        log.warn("Duplicate resource: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(409, ex.getMessage()));
    }

    /**
     * Someone else changed the record since this client read it (or two requests raced on it).
     * The message is safe to show: the user reloads and repeats the change.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleStaleData(OptimisticLockingFailureException ex) {
        log.warn("Stale data: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(409,
                        "This record was changed by someone else. Reload it and repeat your change."));
    }

    /**
     * Handles Oracle unique constraint violations (e.g., duplicate email or employee code)
     * that slip past application-level checks and surface as JDBC errors.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.error("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());

        String message = resolveDataIntegrityMessage(ex);
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(409, message));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 422 Unprocessable Entity
    // ──────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessRule(BusinessRuleException ex) {
        log.warn("Business rule violation [{}]: {}", ex.getRuleCode(), ex.getMessage());

        Map<String, Object> errorDetail = new HashMap<>();
        if (ex.getRuleCode() != null) {
            errorDetail.put("ruleCode", ex.getRuleCode());
            errorDetail.put("reason", ex.getMessage());
            return ResponseEntity
                    .status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ApiResponse.error(422, ex.getMessage(), errorDetail));
        }
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(422, ex.getMessage()));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 500 Internal Server Error — catch-all
    // ──────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAll(
            Exception ex, HttpServletRequest request) {

        // Framework errors that already carry their own status (405, 415, 404 for unknown paths,
        // oversized uploads, missing parts...) must not be reported as 500.
        if (ex instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());
            String reason = status != null ? status.getReasonPhrase() : "Bad request";
            log.warn("Request rejected on [{}] with {}: {}", request.getRequestURI(),
                    errorResponse.getStatusCode().value(), ex.getMessage());
            return ResponseEntity
                    .status(errorResponse.getStatusCode())
                    .body(ApiResponse.error(errorResponse.getStatusCode().value(), reason));
        }

        log.error("Unhandled exception on [{}]: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500,
                        "An unexpected error occurred. Please contact support if the problem persists."));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    private String extractEnumErrorMessage(HttpMessageNotReadableException ex) {
        String cause = ex.getMostSpecificCause().getMessage();
        if (cause != null && cause.contains("not one of the values accepted")) {
            return "Invalid value provided. " + cause.replaceAll("\\[.*?\\]", "").trim();
        }
        return "Invalid or malformed request body. Please check your JSON format and field values.";
    }

    private String resolveDataIntegrityMessage(DataIntegrityViolationException ex) {
        String causeMsg = ex.getMostSpecificCause().getMessage().toLowerCase();

        if (causeMsg.contains("uq_employees_code"))         return "Employee code is already in use.";
        if (causeMsg.contains("uq_employees_work_email"))   return "Work email address is already registered.";
        if (causeMsg.contains("uq_employees_personal_email")) return "Personal email address is already registered.";
        if (causeMsg.contains("unique"))                    return "A record with the same unique field already exists.";

        return "A data integrity constraint was violated. Please review your input.";
    }
}
