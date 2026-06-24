package com.hrms.attendance.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Filter request for querying attendance summaries.
 * Supports single-employee monthly view and HR department-wide view.
 */
public record AttendanceSummaryFilterRequest(

        // Optional — null means fetch all employees (HR/Manager view)
        Long employeeId,

        @NotNull(message = "Year is required")
        @Min(value = 2000, message = "Year must be 2000 or later")
        @Max(value = 2100, message = "Year must be 2100 or earlier")
        Integer year,

        @NotNull(message = "Month is required")
        @Min(value = 1,  message = "Month must be between 1 and 12")
        @Max(value = 12, message = "Month must be between 1 and 12")
        Integer month
) {}
