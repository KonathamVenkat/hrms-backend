package com.hrms.attendance.service.impl;

import com.hrms.attendance.entity.AttendanceLog;
import com.hrms.attendance.enums.AttendanceStatus;
import com.hrms.attendance.repository.OvertimeRequestRepository;
import com.hrms.employee.entity.EmployeeJobDetails;
import com.hrms.employee.entity.WorkShift;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.repository.WorkShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * The single place that turns punch times + the employee's shift into late / working /
 * overtime / early-leave minutes and the day's status. Shared by live check-in/out and by
 * regularization approval so a corrected log is scored by exactly the same rules.
 */
@Component
@RequiredArgsConstructor
public class AttendanceCalculator {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final EmployeeJobDetailsRepository jobDetailsRepo;
    private final WorkShiftRepository          workShiftRepo;
    private final OvertimeRequestRepository    otRepo;

    /** The employee's current shift, or null if none is assigned. */
    public WorkShift resolveShift(Long employeeId) {
        EmployeeJobDetails jd = jobDetailsRepo.findByEmployeeIdAndIsCurrent(employeeId, 1).orElse(null);
        if (jd == null || jd.getShiftId() == null) return null;
        return workShiftRepo.findById(jd.getShiftId()).orElse(null);
    }

    /** Sets check-in time, late minutes and PRESENT/LATE on the log. */
    public void applyCheckIn(AttendanceLog log, LocalDateTime checkIn, WorkShift shift) {
        int lateMinutes = 0;
        AttendanceStatus status = AttendanceStatus.PRESENT;

        if (shift != null) {
            LocalDateTime shiftStart = log.getAttendanceDate().atTime(LocalTime.parse(shift.getStartTime(), HHMM));
            int grace = shift.getGracePeriod() != null ? shift.getGracePeriod() : 0;
            if (checkIn.isAfter(shiftStart.plusMinutes(grace))) {
                lateMinutes = (int) Duration.between(shiftStart, checkIn).toMinutes();
                status = AttendanceStatus.LATE;
            }
        }
        log.setCheckInTime(checkIn);
        log.setLateMinutes(lateMinutes);
        log.setStatus(status);
    }

    /**
     * Sets check-out time, working / overtime / early-leave minutes and the final status.
     * The log's check-in and late status must already be set. Overtime is the larger of the
     * time worked beyond the shift and the total approved overtime for that date — never
     * their sum, which would pay the same hours twice.
     */
    public void applyCheckOut(AttendanceLog log, LocalDateTime checkOut, WorkShift shift) {
        int workingMins = (int) Duration.between(log.getCheckInTime(), checkOut).toMinutes();
        int overtimeMins = 0;
        int earlyLeaveMins = 0;

        if (shift != null) {
            int expectedMins = (int) (shift.getWorkingHours().doubleValue() * 60);
            if (workingMins > expectedMins) {
                overtimeMins = workingMins - expectedMins;
            } else {
                earlyLeaveMins = expectedMins - workingMins;
            }
        }
        Long approved = otRepo.sumApprovedMinutes(log.getEmployeeId(), log.getAttendanceDate());
        if (approved != null && approved > overtimeMins) {
            overtimeMins = approved.intValue();
        }

        log.setCheckOutTime(checkOut);
        log.setWorkingMinutes(workingMins);
        log.setOvertimeMinutes(overtimeMins);
        log.setEarlyLeaveMinutes(earlyLeaveMins);
        log.setStatus(resolveStatus(log.getStatus(), workingMins, shift));
    }

    private AttendanceStatus resolveStatus(AttendanceStatus current, int workingMins, WorkShift shift) {
        if (current == AttendanceStatus.LATE) return AttendanceStatus.LATE;
        if (shift == null) return AttendanceStatus.PRESENT;
        int halfThreshold = (int) (shift.getWorkingHours().doubleValue() * 60 / 2);
        return workingMins < halfThreshold ? AttendanceStatus.HALF_DAY : AttendanceStatus.PRESENT;
    }

    /** Rejects nonsense month/year path params with a 4xx instead of letting them surface as a 500. */
    public static void validateYearMonth(int year, int month) {
        if (month < 1 || month > 12 || year < 2000 || year > 2100) {
            throw new com.hrms.common.exception.BusinessRuleException(
                    "INVALID_PERIOD", "Invalid year/month.");
        }
    }
}
