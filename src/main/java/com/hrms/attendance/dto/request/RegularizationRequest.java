package com.hrms.attendance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for submitting a new attendance regularization.
 * Employee provides the date, requested times, and reason.
 */
public record RegularizationRequest(

        @NotNull(message = "Employee ID is required")
        Long employeeId,

        @NotNull(message = "Attendance date is required")
        String attendanceDate,          // "yyyy-MM-dd"

        @NotNull(message = "Requested check-in time is required")
        String requestedInTime,         // "yyyy-MM-dd'T'HH:mm:ss"

        // Optional — employee may only be regularizing check-in
        String requestedOutTime,        // "yyyy-MM-dd'T'HH:mm:ss"

        @NotBlank(message = "Reason is required")
        @Size(min = 10, max = 500, message = "Reason must be between 10 and 500 characters")
        String reason
) {}
