package com.hrms.leave.dto.response;

import lombok.*;
import java.util.List;

@Data
@Builder
public class CalendarDayResponse {
    private String  date;            // YYYY-MM-DD
    private int     dayOfMonth;
    private String  dayOfWeek;       // MONDAY … SUNDAY
    private boolean isWeekend;
    private boolean isHoliday;
    private boolean isToday;
    private boolean isCurrentMonth;

    private List<CalendarHolidayEntry> holidays;
    private List<CalendarLeaveEntry>   leaves;

    private int onLeaveCount;        // approved leaves on this day
    private int pendingCount;        // pending leaves on this day
}
