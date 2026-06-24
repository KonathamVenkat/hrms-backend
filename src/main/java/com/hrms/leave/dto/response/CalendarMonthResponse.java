package com.hrms.leave.dto.response;

import lombok.*;
import java.util.List;

@Data
@Builder
public class CalendarMonthResponse {
    private int    year;
    private int    month;
    private String monthName;       // e.g. "June 2026"

    private List<CalendarDayResponse> days;

    // Month summary stats
    private int totalApproved;
    private int totalPending;
    private int totalHolidays;
    private int onLeaveToday;

    // Employees on leave today
    private List<CalendarLeaveEntry> todaysLeaves;
}
