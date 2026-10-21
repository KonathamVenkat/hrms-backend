package com.hrms.recruitment.dto.response;

import com.hrms.common.enums.EmploymentType;
import com.hrms.recruitment.enums.RequisitionPriority;
import com.hrms.recruitment.enums.RequisitionStatus;
import com.hrms.recruitment.enums.RequisitionType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Full view of one requisition, with the names a screen needs and the approval decisions so far. */
@Builder
public record RequisitionResponse(
    Long id,
    String code,
    String jobTitle,
    String jobTitleAr,
    Long departmentId,
    String departmentName,
    Long designationId,
    String designationTitle,
    Long locationId,
    Long reportingManagerId,
    String reportingManagerName,
    Long requestedById,
    String requestedByName,
    Integer noOfPositions,
    Integer filledPositions,
    EmploymentType employmentType,
    RequisitionType requisitionType,
    Long replacedEmployeeId,
    BigDecimal minExperienceYears,
    BigDecimal maxExperienceYears,
    BigDecimal minSalary,
    BigDecimal maxSalary,
    String currency,
    RequisitionPriority priority,
    LocalDate targetJoinDate,
    String justification,
    RequisitionStatus status,
    LocalDateTime closedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Long version,
    List<ApprovalResponse> approvals
) { }
