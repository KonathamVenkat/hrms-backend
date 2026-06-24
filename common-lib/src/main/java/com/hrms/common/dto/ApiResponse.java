package com.hrms.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Generic API response envelope used by all HRMS microservices.
 *
 * <p>Angular frontend should always expect this wrapper and handle
 * {@code success}, {@code message}, and {@code data} fields accordingly.</p>
 *
 * <p>Fields with {@code null} values are omitted from JSON output
 * via {@link JsonInclude.Include#NON_NULL}.</p>
 *
 * @param <T> the type of the response payload
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /** Indicates whether the request was processed successfully. */
    private boolean success;

    /** Human-readable message describing the outcome. */
    private String message;

    /** The actual response payload. */
    private T data;

    /** HTTP status code (mirrors the HTTP response code for client convenience). */
    private int statusCode;

    /** Server timestamp when the response was generated. */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /** Error details, populated only when {@code success} is false. */
    private Object errors;

    // ──────────────────────────────────────────────────────────────────────────
    // Factory helpers
    // ──────────────────────────────────────────────────────────────────────────

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message("Request processed successfully")
                .data(data)
                .statusCode(200)
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .statusCode(200)
                .build();
    }

    public static <T> ApiResponse<T> created(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .statusCode(201)
                .build();
    }

    public static <T> ApiResponse<T> error(int statusCode, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .statusCode(statusCode)
                .build();
    }

    public static <T> ApiResponse<T> error(int statusCode, String message, Object errors) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .statusCode(statusCode)
                .errors(errors)
                .build();
    }

    public static <T> ApiResponse<T> noContent(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .statusCode(204)
                .build();
    }
}
