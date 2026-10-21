package com.hrms.recruitment.dto.response;

import com.hrms.recruitment.enums.RequisitionPriority;
import com.hrms.recruitment.enums.RequisitionStatus;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** One row of the requisition list. No salary and no justification: the list is for finding, not for reading. */
@Builder
public record RequisitionSummaryResponse(
    Long id,
    String code,
    String jobTitle,
    Long departmentId,
    String departmentName,
    String requestedByName,
    Integer noOfPositions,
    Integer filledPositions,
    RequisitionPriority priority,
    LocalDate targetJoinDate,
    RequisitionStatus status,
    LocalDateTime createdAt
) { }
