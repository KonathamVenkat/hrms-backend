package com.hrms.leave.dto.response;

import lombok.*;

@Data @Builder
public class LeaveBalanceResponse {

    private Long    balanceId;
    private Long    employeeId;

    // Leave type info
    private String  leaveType;           // code e.g. "ANNUAL"
    private String  leaveTypeName;       // display name e.g. "Annual Leave"
    private String  leaveTypeNameAr;
    private Boolean isPaid;
    private Boolean isCarryForward;
    private Boolean requiresDocument;    // leave type needs a supporting attachment
    private Integer docMaxFileSizeMb;    // upload limits for that attachment
    private String  docAllowedExtensions;

    // Balance figures
    private Integer year;
    private Double  totalDays;
    private Double  usedDays;
    private Double  pendingDays;
    private Double  availableDays;       // computed: total - used - pending
    private Double  carriedForwardDays;  // days carried from previous year

    // Audit
    private String  createdBy;
    private String  createdAt;
    private String  updatedAt;
}

