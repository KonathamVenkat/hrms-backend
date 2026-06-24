package com.hrms.attendance.dto.response;

import com.hrms.attendance.enums.AttendanceStatus;
import com.hrms.attendance.enums.PunchSource;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AttendanceLogResponse(
        Long        logId,
        Long        employeeId,
        String      employeeCode,
        String      employeeName,           // resolved from Employee service
        LocalDate   attendanceDate,
        LocalDateTime checkInTime,
        LocalDateTime checkOutTime,
        Integer     workingMinutes,
        Integer     overtimeMinutes,
        Integer     lateMinutes,
        Integer     earlyLeaveMinutes,
        AttendanceStatus status,
        PunchSource punchSource,
        Long        locationId,
        String      locationName,           // resolved from Office Locations
        String      shiftName,             // resolved from Work Shifts via Job Details
        String      shiftStartTime,        // HH:mm from WORK_SHIFTS
        String      shiftEndTime,
        String      notes,
        Boolean     isRegularized,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
