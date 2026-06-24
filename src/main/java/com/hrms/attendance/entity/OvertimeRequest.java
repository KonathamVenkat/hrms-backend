package com.hrms.attendance.entity;

import com.hrms.attendance.enums.OvertimeType;
import com.hrms.attendance.enums.RegularizationStatus;
import com.hrms.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents an employee's overtime work request.
 *
 * OT_ID format: OT-YYYY-NNNNNN (e.g. OT-2026-000001)
 * Generated in Java via SEQ_OVERTIME_REQ.NEXTVAL before persist()
 * so Hibernate never sees a null String PK.
 *
 * The Oracle trigger TRG_BI_OVERTIME_REQ acts as a DB-level safety net
 * in case of direct inserts (DBeaver / SQL scripts).
 *
 * @GeneratedValue(generator = "assigned") tells Hibernate:
 * "I will always assign the ID myself — do not auto-generate."
 * This suppresses the IdentifierGenerationException for String PKs.
 */
@Entity
@Table(name = "OVERTIME_REQUESTS", schema = "HRMS")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class OvertimeRequest extends Auditable {

    @Id
    @GeneratedValue(generator = "assigned")          // ← tells Hibernate: user-assigned
    @GenericGenerator(name = "assigned",
            strategy = "assigned")                   // ← no auto-generation
    @Column(name = "OT_ID", nullable = false, length = 30)
    private String otId;                             // set in service before save()

    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

    @Column(name = "EMPLOYEE_CODE", nullable = false, length = 30)
    private String employeeCode;

    @Column(name = "OT_DATE", nullable = false)
    private LocalDate otDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "OT_TYPE", nullable = false, length = 20)
    @Builder.Default
    private OvertimeType otType = OvertimeType.POST_FACTO;

    @Column(name = "START_TIME", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "END_TIME", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "DURATION_MINUTES", nullable = false)
    private Integer durationMinutes;

    @Column(name = "REASON", nullable = false, length = 500)
    private String reason;

    @Column(name = "PROJECT_CODE", length = 100)
    private String projectCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    @Builder.Default
    private RegularizationStatus status = RegularizationStatus.PENDING;

    @Column(name = "REJECTION_REASON", length = 500)
    private String rejectionReason;

    @Column(name = "REVIEWED_BY")
    private Long reviewedBy;

    @Column(name = "REVIEWED_AT")
    private LocalDateTime reviewedAt;

    @Column(name = "IS_ACTIVE", nullable = false)
    @Builder.Default
    private Integer isActive = 1;
}