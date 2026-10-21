package com.hrms.recruitment.dto.response;

import com.hrms.recruitment.enums.ApprovalDecision;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ApprovalResponse(
    Integer levelNo,
    Long approverId,
    String approverName,
    ApprovalDecision decision,
    boolean budgetConfirmed,
    boolean managementConfirmed,
    String comments,
    LocalDateTime actionAt
) { }
