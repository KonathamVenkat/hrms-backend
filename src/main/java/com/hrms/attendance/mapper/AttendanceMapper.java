package com.hrms.attendance.mapper;

import com.hrms.attendance.dto.response.AttendanceSummaryResponse;
import com.hrms.attendance.entity.AttendanceSummary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;

@Component
public class AttendanceMapper {

    public AttendanceSummaryResponse toSummaryResponse(
            AttendanceSummary s, String employeeName, boolean isCurrentMonth) {

        String monthLabel = Month.of(s.getSummaryMonth())
                .getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                + " " + s.getSummaryYear();

        String workHours = formatMinutes(s.getTotalWorkingMins());
        String otHours   = formatMinutes(s.getTotalOvertimeMins());

        return new AttendanceSummaryResponse(
                s.getSummaryId(),
                s.getEmployeeId(),
                s.getEmployeeCode(),
                employeeName,
                otHours, otHours, s.getSummaryYear(),
                s.getSummaryMonth(),
                monthLabel,
                // BigDecimal → double for response DTO
                s.getPresentDays()  != null ? s.getPresentDays().doubleValue()  : 0.0,
                s.getAbsentDays()   != null ? s.getAbsentDays().doubleValue()   : 0.0,
                s.getHalfDays()     != null ? s.getHalfDays().doubleValue()     : 0.0,
                s.getLateDays()     != null ? s.getLateDays()     : 0,
                s.getLeaveDays()    != null ? s.getLeaveDays().doubleValue()    : 0.0,
                s.getHolidayDays()  != null ? s.getHolidayDays()  : 0,
                s.getWeekendDays()  != null ? s.getWeekendDays()  : 0,
                null, s.getTotalWorkingMins()  != null ? s.getTotalWorkingMins()  : 0L,
                s.getTotalOvertimeMins() != null ? s.getTotalOvertimeMins() : 0L,
                s.getTotalLateMins()     != null ? s.getTotalLateMins()     : 0L,
                workHours,
                otHours,
                otHours, null, isCurrentMonth,
                s.getLastCalculatedAt()
        );
    }

    private String formatMinutes(Long totalMins) {
        if (totalMins == null || totalMins <= 0) return "0h 0m";
        long hours = totalMins / 60;
        long mins  = totalMins % 60;
        return hours + "h " + mins + "m";
    }
}