package com.hrms.leave.dto.response;

import lombok.*;
import java.math.BigDecimal;

@Data @Builder
public class LeaveTypeResponse {

private Long       leaveTypeId;
private String     code;
private String     nameEn;
private String     nameAr;
private String     description;
private BigDecimal defaultDays;
private Boolean    isPaid;
private Boolean    isCarryForward;
private BigDecimal maxCarryDays;
private Boolean    requiresDocument;
private Integer    docMaxFileSizeMb;
private String     docAllowedExtensions;
private Integer    minNoticeDays;
private Integer    maxConsecutiveDays;
private String     applicableGender;
private Boolean    isActive;
private Integer    sortOrder;
private String     createdBy;
private String     createdAt;
private String     updatedBy;
private String     updatedAt;
}