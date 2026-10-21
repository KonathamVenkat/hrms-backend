package com.hrms.recruitment.entity;

import com.hrms.common.util.BooleanToNumberConverter;
import com.hrms.recruitment.enums.ApprovalDecision;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One decision on a requisition (table REQUISITION_APPROVALS). A pending requisition has no row yet.
 * LEVEL_NO is always 1 today; a second approval level can be added without a schema change.
 */
@Entity
@Table(name = "REQUISITION_APPROVALS", schema = "HRMS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RequisitionApproval {

    public static final int FIRST_LEVEL = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "requisition_approvals_seq")
    @SequenceGenerator(name = "requisition_approvals_seq", sequenceName = "HRMS.SEQ_REQUISITION_APPROVALS", allocationSize = 1)
    @Column(name = "APPROVAL_ID", nullable = false)
    private Long id;

    @Column(name = "REQUISITION_ID", nullable = false, updatable = false)
    private Long requisitionId;

    @Column(name = "LEVEL_NO", nullable = false, updatable = false)
    private Integer levelNo;

    @Column(name = "APPROVER_ID", nullable = false, updatable = false)
    private Long approverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20, updatable = false)
    private ApprovalDecision decision;

    @Convert(converter = BooleanToNumberConverter.class)
    @Column(name = "BUDGET_CONFIRMED", nullable = false, updatable = false)
    private Boolean budgetConfirmed;

    @Convert(converter = BooleanToNumberConverter.class)
    @Column(name = "MANAGEMENT_CONFIRMED", nullable = false, updatable = false)
    private Boolean managementConfirmed;

    @Column(name = "COMMENTS", length = 500, updatable = false)
    private String comments;

    @Column(name = "ACTION_AT", nullable = false, updatable = false)
    private LocalDateTime actionAt;
}
