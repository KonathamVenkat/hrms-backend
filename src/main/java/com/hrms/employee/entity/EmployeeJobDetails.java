package com.hrms.employee.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA Entity for HRMS.EMPLOYEE_JOB_DETAILS table.
 *
 * Implements SCD (Slowly Changing Dimension) Type 2:
 *   - Each job assignment is a separate row
 *   - IS_CURRENT = 1 marks the active record
 *   - EFFECTIVE_TO is null for the current record
 *   - When a new assignment is created, the old one gets
 *     EFFECTIVE_TO set and IS_CURRENT = 0
 *
 * FKs:
 *   EMPLOYEE_ID          → EMPLOYEES.EMPLOYEE_ID
 *   DEPARTMENT_ID        → DEPARTMENTS.DEPT_ID
 *   DESIGNATION_ID       → DESIGNATIONS.DESIG_ID
 *   REPORTING_MANAGER_ID → EMPLOYEES.EMPLOYEE_ID
 *   FUNCTIONAL_MANAGER_ID→ EMPLOYEES.EMPLOYEE_ID
 *   LOCATION_ID          → OFFICE_LOCATIONS.LOCATION_ID
 *   SHIFT_ID             → WORK_SHIFTS.SHIFT_ID
 */
@Entity
@Table(
    name   = "EMPLOYEE_JOB_DETAILS",
    schema = "HRMS"
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmployeeJobDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ejd_seq")
    @SequenceGenerator(
        name           = "ejd_seq",
        sequenceName   = "HRMS.SEQ_EMPLOYEE_JOB_DETAILS",
        allocationSize = 1
    )
    @Column(name = "JOB_DETAILS_ID", nullable = false)
    private Long jobDetailsId;

    // ── Employee ──────────────────────────────────────────────
    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

    // ── Organisation ──────────────────────────────────────────
    @Column(name = "DEPARTMENT_ID", nullable = false)
    private Long departmentId;

    @Column(name = "DESIGNATION_ID", nullable = false)
    private Long designationId;

    @Column(name = "JOB_POSITION_ID", length = 20)
    private String jobPositionId;

    // ── Managers ──────────────────────────────────────────────
    @Column(name = "REPORTING_MANAGER_ID")
    private Long reportingManagerId;

    @Column(name = "FUNCTIONAL_MANAGER_ID")
    private Long functionalManagerId;

    // ── Work Location & Shift ─────────────────────────────────
    @Column(name = "LOCATION_ID", nullable = false)
    private Long locationId;

    @Column(name = "SHIFT_ID")
    private Long shiftId;

    /**
     * ON_SITE | REMOTE | HYBRID
     * Maps to CHK_EJOB_WORK_MODE constraint
     */
    @Column(name = "WORK_MODE", nullable = false, length = 20)
    @Builder.Default
    private String workMode = "ON_SITE";

    // ── SCD Type 2 fields ─────────────────────────────────────
    @Column(name = "EFFECTIVE_FROM", nullable = false)
    private LocalDate effectiveFrom;

    /**
     * Null when IS_CURRENT = 1 (still active).
     * Set when a new record supersedes this one.
     */
    @Column(name = "EFFECTIVE_TO")
    private LocalDate effectiveTo;

    /**
     * 1 = this is the active job record for the employee.
     * 0 = historical record.
     */
    @Column(name = "IS_CURRENT", nullable = false)
    @Builder.Default
    private Integer isCurrent = 1;

    @Column(name = "REMARKS", length = 500)
    private String remarks;

    // ── Audit ─────────────────────────────────────────────────
    @Column(name = "CREATED_BY", length = 36)
    private String createdBy;

    @Column(name = "CREATED_AT", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "UPDATED_BY", length = 36)
    private String updatedBy;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;
}
