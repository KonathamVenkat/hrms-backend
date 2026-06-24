package com.hrms.leave.service;

import com.hrms.leave.dto.response.CalendarMonthResponse;

public interface LeaveCalendarService {

    /**
     * Returns full calendar data for a given month/year.
     * Includes approved/pending leaves + public holidays.
     */
    CalendarMonthResponse getMonthCalendar(int year, int month);

    /** Returns calendar for the current month. */
    CalendarMonthResponse getCurrentMonthCalendar();
}
