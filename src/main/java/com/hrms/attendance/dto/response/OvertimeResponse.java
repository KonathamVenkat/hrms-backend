package com.hrms.attendance.dto.response;

import com.hrms.attendance.enums.OvertimeType;
import com.hrms.attendance.enums.RegularizationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for overtime requests.
 * Returned on submit, list, approve, reject and cancel operations.
 */
public record OvertimeResponse(
        String                  otId,               // "OT-2026-000001"
        Long                    employeeId,
        String                  employeeCode,
        String                  employeeName,
        LocalDate               otDate,
        String                  otDateFormatted,    // "Mon, Jun 14 2026"
        OvertimeType            otType,
        String                  otTypeLabel,        // "Post Facto"
        LocalDateTime           startTime,
        LocalDateTime           endTime,
        Integer                 durationMinutes,
        String                  durationFormatted,  // "2h 30m"
        String                  reason,
        String                  projectCode,
        RegularizationStatus    status,
        String                  statusLabel,
        String                  rejectionReason,
        Long                    reviewedBy,
        String                  reviewedByName,
        LocalDateTime           reviewedAt,
        Boolean                 isActive,
        LocalDateTime           createdAt,
        LocalDateTime           updatedAt
) {}
