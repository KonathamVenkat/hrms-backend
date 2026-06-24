package com.hrms.leave.dto.response;

import lombok.*;

@Data
@Builder
public class CalendarLeaveEntry {
    private Long   employeeId;
    private String employeeCode;
    private String employeeName;
    private String leaveTypeCode;
    private String leaveTypeName;
    private String status;
    private String startDate;
    private String endDate;
}
