package com.hrms.leave.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA Entity for HRMS.LEAVE_REQUESTS table.
 *
 * DB columns (from HRMS_SCRIPT.txt):
 *   LEAVE_REQ_ID, EMPLOYEE_ID, EMPLOYEE_CODE,
 *   LEAVE_TYPE, START_DATE, END_DATE, TOTAL_DAYS,
 *   REASON, REJECTION_REASON, STATUS,
 *   APPROVED_BY, APPROVED_AT,
 *   IS_ACTIVE,
 *   CREATED_AT, CREATED_BY, UPDATED_AT, UPDATED_BY
 *
 * Note: Does NOT extend Auditable because the DB uses
 *       IS_ACTIVE BOOLEAN which conflicts with Auditable
 *       — audit fields managed manually here.
 */
@Entity
@Table(name = "LEAVE_REQUESTS", schema = "HRMS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
                    generator = "leave_req_seq")
    @SequenceGenerator(
        name           = "leave_req_seq",
        sequenceName   = "HRMS.LEAVE_REQ_SEQ",
        allocationSize = 1
    )
    @Column(name = "LEAVE_REQ_ID")
    private Long leaveReqId;

    // ── Employee ──────────────────────────────────────────────
    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

    @Column(name = "EMPLOYEE_CODE", nullable = false, length = 20)
    private String employeeCode;

    // ── Leave details ─────────────────────────────────────────

    /**
     * Stores leave type code e.g. "ANNUAL", "SICK".
     * Maps to LEAVE_TYPE column — named leaveTypeCode for clarity.
     */
    @Column(name = "LEAVE_TYPE", nullable = false, length = 20)
    private String leaveTypeCode;

    @Column(name = "START_DATE", nullable = false)
    private LocalDate startDate;

    @Column(name = "END_DATE", nullable = false)
    private LocalDate endDate;

    @Column(name = "TOTAL_DAYS", nullable = false)
    private Double totalDays;

    @Column(name = "REASON", length = 500)
    private String reason;

    // ── Status ────────────────────────────────────────────────

    /**
     * PENDING | APPROVED | REJECTED | CANCELLED
     * Stored as VARCHAR2 in DB, mapped to LeaveStatus enum.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    @Builder.Default
    private LeaveStatus status = LeaveStatus.PENDING;

    // ── Approval / Rejection ──────────────────────────────────

    @Column(name = "APPROVED_BY", length = 50)
    private String approvedBy;

    @Column(name = "APPROVED_AT")
    private LocalDateTime approvedAt;

    /**
     * Filled when status = REJECTED.
     * Maps to REJECTION_REASON column.
     */
    @Column(name = "REJECTION_REASON", length = 500)
    private String rejectionReason;

    // ── Supporting document (optional; required when LeaveType.requiresDocument) ──

    /** Path relative to app.leave-upload.dir, e.g. "employee_12/uuid.pdf". */
    @Column(name = "ATTACHMENT_PATH", length = 300)
    private String attachmentPath;

    @Column(name = "ATTACHMENT_NAME", length = 255)
    private String attachmentName;

    @Column(name = "ATTACHMENT_SIZE")
    private Long attachmentSize;

    // ── Active flag ───────────────────────────────────────────

    /**
     * IS_ACTIVE stored as BOOLEAN in Oracle 23ai.
     * Uses @Column with columnDefinition for compatibility.
     */
    @Column(name = "IS_ACTIVE", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // ── Audit ─────────────────────────────────────────────────

    @Column(name = "CREATED_BY", length = 36)
    private String createdBy;

    @Column(name = "CREATED_AT", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "UPDATED_BY", length = 36)
    private String updatedBy;

    @Column(name = "UPDATED_AT")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}