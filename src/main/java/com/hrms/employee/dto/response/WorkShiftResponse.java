// ── WorkShiftResponse.java ────────────────────────────────────
package com.hrms.employee.dto.response;

import java.math.BigDecimal;

import lombok.*;

@Data @Builder
public class WorkShiftResponse {

    private Long    shiftId;
    private String  shiftCode;
    private String  shiftName;
    private String  shiftNameAr;
    private String  shiftType;
    private String  startTime;
    private String  endTime;
    private Integer breakDuration;
    private BigDecimal workingHours;
    private Integer gracePeriod;
    private String  workingDays;
    private Boolean isOvernight;
    private Boolean isFlexible;
    private String  description;
    private Boolean isActive;
    private Integer sortOrder;
    private String  createdBy;
    private String  createdAt;
    private String  updatedAt;
}
