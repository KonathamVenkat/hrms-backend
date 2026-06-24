package com.hrms.leave.dto.response;

import lombok.*;

@Data
@Builder
public class CalendarHolidayEntry {
    private Long   holidayId;
    private String holidayName;
    private String holidayNameAr;
    private String holidayType;    // PUBLIC | RELIGIOUS | OPTIONAL | RESTRICTED
}
