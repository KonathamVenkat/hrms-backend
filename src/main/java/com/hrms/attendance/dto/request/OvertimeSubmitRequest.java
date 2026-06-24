package com.hrms.attendance.dto.request;

import com.hrms.attendance.enums.OvertimeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for submitting a new overtime request.
 * Employee provides the date, times worked, type and reason.
 */
public record OvertimeSubmitRequest(

        @NotNull(message = "Employee ID is required")
        Long employeeId,

        @NotNull(message = "Overtime date is required")
        String otDate,                  // "yyyy-MM-dd"

        @NotNull(message = "Overtime type is required")
        OvertimeType otType,

        @NotNull(message = "Start time is required")
        String startTime,               // "yyyy-MM-dd'T'HH:mm:ss"

        @NotNull(message = "End time is required")
        String endTime,                 // "yyyy-MM-dd'T'HH:mm:ss"

        @NotBlank(message = "Reason is required")
        @Size(min = 10, max = 500, message = "Reason must be between 10 and 500 characters")
        String reason,

        @Size(max = 100, message = "Project code cannot exceed 100 characters")
        String projectCode              // optional
) {}
