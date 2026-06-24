package com.hrms.attendance.dto.response;

import com.hrms.attendance.enums.RegularizationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for attendance regularization requests.
 * Returned on submit, list, approve, reject and cancel operations.
 */
public record RegularizationResponse(
        Long                    regId,
        Long                    employeeId,
        String                  employeeCode,
        String                  employeeName,
        LocalDate               attendanceDate,
        String                  attendanceDateFormatted,   // "Mon, Jun 14 2026"
        Long                    logId,
        LocalDateTime           requestedInTime,
        LocalDateTime           requestedOutTime,
        String                  reason,
        RegularizationStatus    status,
        String                  statusLabel,               // "Pending Approval"
        String                  rejectionReason,
        Long                    reviewedBy,
        String                  reviewedByName,
        LocalDateTime           reviewedAt,
        Boolean                 isActive,
        LocalDateTime           createdAt,
        LocalDateTime           updatedAt
) {}
