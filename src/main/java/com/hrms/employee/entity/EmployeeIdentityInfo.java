package com.hrms.employee.entity;

import com.hrms.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/**
 * JPA Entity for HRMS.EMPLOYEE_IDENTITY_INFO table.
 * One-to-one with EMPLOYEES — only one identity record per employee.
 * Stores national ID, passport, visa, work permit, driving license.
 */
@Entity
@Table(name = "EMPLOYEE_IDENTITY_INFO", schema = "HRMS",
    uniqueConstraints = @UniqueConstraint(
        name = "UQ_EMP_IDENTITY_EMP_ID",
        columnNames = "EMPLOYEE_ID"
    )
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmployeeIdentityInfo extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
                    generator = "emp_identity_seq")
    @SequenceGenerator(
        name           = "emp_identity_seq",
        sequenceName   = "HRMS.SEQ_EMPLOYEE_IDENTITY_INFO",
        allocationSize = 1
    )
    @Column(name = "EMPLOYEE_IDENTITY_ID", nullable = false)
    private Long employeeIdentityId;

    // ── Employee FK ──────────────────────────────────────────
    @Column(name = "EMPLOYEE_ID", nullable = false, unique = true)
    private Long employeeId;

    // ── National Identity ────────────────────────────────────
    @Column(name = "NATIONAL_ID", length = 200)
    private String nationalId;

    // ── Passport ─────────────────────────────────────────────
    @Column(name = "PASSPORT_NUMBER", length = 200)
    private String passportNumber;

    // ── Tax & Social Security ─────────────────────────────────
    @Column(name = "TAX_ID", length = 200)
    private String taxId;

    @Column(name = "SOCIAL_SECURITY_NUMBER", length = 200)
    private String socialSecurityNumber;

    // ── Driving License ───────────────────────────────────────
    @Column(name = "DRIVING_LICENSE_NUMBER", length = 100)
    private String drivingLicenseNumber;

    // ── Visa ──────────────────────────────────────────────────
    @Column(name = "VISA_NUMBER", length = 100)
    private String visaNumber;

    @Column(name = "VISA_TYPE", length = 50)
    private String visaType;

    @Column(name = "VISA_ISSUE_DATE")
    private LocalDate visaIssueDate;

    @Column(name = "VISA_EXPIRY_DATE")
    private LocalDate visaExpiryDate;

    // ── Work Permit ───────────────────────────────────────────
    @Column(name = "WORK_PERMIT_NUMBER", length = 100)
    private String workPermitNumber;

    @Column(name = "WORK_PERMIT_EXPIRY")
    private LocalDate workPermitExpiry;

    // ── Biometric ─────────────────────────────────────────────
    @Column(name = "BIOMETRIC_ID", length = 100)
    private String biometricId;

    // ── Audit ─────────────────────────────────────────────────
    /** Optimistic lock: bumped on every update; a stale writer gets HTTP 409. */
    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;
}
