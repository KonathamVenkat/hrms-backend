package com.hrms.attendance.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for attendance monthly summary.
 * Used for both single-employee and HR department-wide views.
 * BigDecimal fields (presentDays etc.) are converted to Double for JSON clarity.
 */
public record AttendanceSummaryResponse(
        Long            summaryId,
        Long            employeeId,
        String          employeeCode,
        String          employeeName,
        String          departmentName,
        String          designationName,
        Integer         summaryYear,
        Integer         summaryMonth,
        String          monthLabel,          // "June 2026"

        // ── Day counters ─────────────────────────────────────
        Double          presentDays,
        Double          absentDays,
        Double          halfDays,
        Integer         lateDays,
        Double          leaveDays,
        Integer         holidayDays,
        Integer         weekendDays,
        Integer         totalWorkingDays,    // presentDays + halfDays + lateDays

        // ── Time accumulators ─────────────────────────────────
        Long            totalWorkingMins,
        Long            totalOvertimeMins,
        Long            totalLateMins,

        // ── Formatted display fields ──────────────────────────
        String          totalWorkingHours,   // "168h 30m"
        String          totalOvertimeHours,  // "4h 15m"
        String          totalLateHours,      // "0h 45m"
        Double          attendancePercentage, // presentDays / workingDays * 100

        // ── Meta ──────────────────────────────────────────────
        Boolean         isCurrentMonth,
        LocalDateTime   lastCalculatedAt
) {}
