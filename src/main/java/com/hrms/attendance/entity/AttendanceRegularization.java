package com.hrms.attendance.entity;

import com.hrms.attendance.enums.RegularizationStatus;
import com.hrms.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents an employee's request to correct their attendance record.
 * Submitted when an employee missed punching in/out due to system issues,
 * forgot to punch, or was on field duty without biometric access.
 *
 * Approval flow:
 *   Employee submits → HR_MANAGER/HR_ADMIN reviews → APPROVED/REJECTED
 *   On APPROVED → AttendanceLog record is corrected automatically.
 */
@Entity
@Table(name = "ATTENDANCE_REGULARIZATION", schema = "HRMS")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AttendanceRegularization extends Auditable {

    @Id
    @Column(name = "REG_ID", nullable = false)
    private Long regId;

    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

    @Column(name = "EMPLOYEE_CODE", nullable = false, length = 30)
    private String employeeCode;

    @Column(name = "ATTENDANCE_DATE", nullable = false)
    private LocalDate attendanceDate;

    // FK to ATTENDANCE_LOGS — null if no log exists for that date yet
    @Column(name = "LOG_ID")
    private Long logId;

    @Column(name = "REQUESTED_IN_TIME", nullable = false)
    private LocalDateTime requestedInTime;

    @Column(name = "REQUESTED_OUT_TIME")
    private LocalDateTime requestedOutTime;

    @Column(name = "REASON", nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    @Builder.Default
    private RegularizationStatus status = RegularizationStatus.PENDING;

    @Column(name = "REJECTION_REASON", length = 500)
    private String rejectionReason;

    // Employee ID of the manager/HR who approved or rejected
    @Column(name = "REVIEWED_BY")
    private Long reviewedBy;

    @Column(name = "REVIEWED_AT")
    private LocalDateTime reviewedAt;

    @Column(name = "IS_ACTIVE", nullable = false)
    @Builder.Default
    private Integer isActive = 1;
}
