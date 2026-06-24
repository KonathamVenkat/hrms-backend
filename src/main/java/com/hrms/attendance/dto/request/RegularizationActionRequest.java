package com.hrms.attendance.dto.request;

import com.hrms.attendance.enums.RegularizationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for HR/Manager to approve or reject a regularization.
 * action must be APPROVED or REJECTED.
 * rejectionReason is required when action = REJECTED.
 */
public record RegularizationActionRequest(

        @NotNull(message = "Reviewer employee ID is required")
        Long reviewedBy,

        @NotNull(message = "Action is required")
        RegularizationStatus action,    // APPROVED or REJECTED only

        @Size(max = 500, message = "Rejection reason cannot exceed 500 characters")
        String rejectionReason          // required when action = REJECTED
) {}
