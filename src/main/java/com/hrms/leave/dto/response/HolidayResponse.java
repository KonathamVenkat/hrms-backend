package com.hrms.leave.dto.response;

import lombok.*;

@Data @Builder
public class HolidayResponse {

    private Long    holidayId;
    private String  holidayName;
    private String  holidayNameAr;
    private String  holidayDate;      // formatted YYYY-MM-DD
    private String  holidayType;
    private String  description;
    private Boolean isRecurring;
    private Integer year;
    private Boolean isActive;
    private String  createdBy;
    private String  createdAt;
    private String  updatedAt;
}
