package com.hrms.leave.dto.response;

import lombok.*;

@Data @Builder
public class LeaveRequestResponse {

    private Long    leaveReqId;
    private Long    employeeId;
    private String  employeeCode;
    private String  employeeName;

    // Leave type
    private String  leaveTypeCode;
    private String  leaveTypeName;
    private Boolean isPaid;

    // Dates and days
    private String  startDate;
    private String  endDate;
    private Double  totalDays;
    private String  reason;

    // Status
    private String  status;
    private String  approvedBy;
    private String  approvedAt;
    private String  remarks;

    // Balance snapshot — shown to employee when applying
    private Double  balanceAvailable;
    private Double  balanceTotal;

    // Audit
    private String  createdAt;
    private String  updatedAt;
}
