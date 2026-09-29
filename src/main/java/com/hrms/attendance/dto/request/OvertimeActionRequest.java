package com.hrms.attendance.dto.request;

import com.hrms.attendance.enums.RegularizationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for HR/Manager to approve or reject an overtime request.
 * action must be APPROVED or REJECTED only.
 * rejectionReason is mandatory when action = REJECTED.
 */
public record OvertimeActionRequest(

        @NotNull(message = "Action is required — APPROVED or REJECTED")
        RegularizationStatus action,

        @Size(max = 500, message = "Rejection reason cannot exceed 500 characters")
        String rejectionReason
) {}
