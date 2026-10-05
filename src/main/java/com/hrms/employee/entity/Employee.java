package com.hrms.employee.entity;

import com.hrms.common.audit.Auditable;
import com.hrms.common.enums.BloodGroup;
import com.hrms.common.enums.EmploymentStatus;
import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import com.hrms.common.enums.MaritalStatus;
import com.hrms.common.util.BloodGroupConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

import com.hrms.common.util.BooleanToNumberConverter;
import jakarta.persistence.Convert;
/**
 * JPA entity representing the {@code HRMS.EMPLOYEES} Oracle table.
 *
 * <h3>Schema notes:</h3>
 * <ul>
 *   <li>Primary key is a 20-digit Oracle NUMBER — mapped to {@code Long} (BIGINT).</li>
 *   <li>Arabic name fields use {@code NVARCHAR2} in Oracle — mapped to {@code String}
 *       with {@code @Column(columnDefinition = "NVARCHAR2(200)")} to preserve Unicode.</li>
 *   <li>{@link BloodGroup} uses a custom {@link BloodGroupConverter} because Oracle stores
 *       the value as "A+", "O-" etc. (label), not the Java enum name.</li>
 *   <li>All other enum fields ({@link Gender}, {@link MaritalStatus}, {@link EmploymentStatus},
 *       {@link EmploymentType}) use {@code @Enumerated(EnumType.STRING)} since Oracle
 *       constraint values match the enum names exactly.</li>
 *   <li>Audit fields (createdBy, createdAt, updatedBy, updatedAt) are inherited
 *       from {@link Auditable} via Spring Data JPA auditing.</li>
 *   <li>{@code isActive} maps to Oracle {@code NUMBER(1,0) DEFAULT 1} — used for
 *       soft deletes across the HRMS system.</li>
 * </ul>
 */
@Entity
@Table(
    name = "EMPLOYEES",
    schema = "HRMS",
    uniqueConstraints = {
        @UniqueConstraint(name = "UQ_EMPLOYEES_CODE",           columnNames = "EMPLOYEE_CODE"),
        @UniqueConstraint(name = "UQ_EMPLOYEES_PERSONAL_EMAIL", columnNames = "PERSONAL_EMAIL"),
        @UniqueConstraint(name = "UQ_EMPLOYEES_WORK_EMAIL",     columnNames = "WORK_EMAIL")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"firstNameAr", "middleNameAr", "lastNameAr"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Employee extends Auditable {

    // ──────────────────────────────────────────────────────────────────────────
    // Primary Key
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Oracle NUMBER(20,0) — mapped to Long.
     * Uses an Oracle sequence for auto-generation; define the sequence
     * in Liquibase/Flyway as: CREATE SEQUENCE HRMS.EMP_SEQ START WITH 1 INCREMENT BY 1
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "emp_seq")
    @SequenceGenerator(
        name       = "emp_seq",
        sequenceName = "HRMS.SEQ_EMPLOYEES",
        allocationSize = 1
    )
    @Column(name = "EMPLOYEE_ID", nullable = false, precision = 20)
    @EqualsAndHashCode.Include
    private Long id;

    // ──────────────────────────────────────────────────────────────────────────
    // Identity & Code
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Unique human-readable employee identifier (e.g., "EMP-2024-001").
     * Maps to Oracle: EMPLOYEE_CODE VARCHAR2(30) NOT NULL UNIQUE
     */
    @Column(name = "EMPLOYEE_CODE", unique = true, length = 30,nullable   = false,insertable = true,updatable  = false)
    private String employeeCode;

    // ──────────────────────────────────────────────────────────────────────────
    // Name Fields (English + Arabic)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Maps to Oracle: FIRST_NAME VARCHAR2(100) NOT NULL
     */
    @Column(name = "FIRST_NAME", nullable = false, length = 100)
    private String firstName;

    /**
     * Arabic first name. Maps to Oracle: FIRST_NAME_AR NVARCHAR2(200) NOT NULL
     * NVARCHAR2 is used for multi-byte character support (Arabic script).
     */
    @Column(name = "FIRST_NAME_AR", nullable = false, columnDefinition = "NVARCHAR2(200)")
    private String firstNameAr;

    /**
     * Maps to Oracle: MIDDLE_NAME VARCHAR2(100) — nullable
     */
    @Column(name = "MIDDLE_NAME", length = 100)
    private String middleName;

    /**
     * Arabic middle name. Maps to Oracle: MIDDLE_NAME_AR NVARCHAR2(200) — nullable
     */
    @Column(name = "MIDDLE_NAME_AR", columnDefinition = "NVARCHAR2(200)")
    private String middleNameAr;

    /**
     * Maps to Oracle: LAST_NAME VARCHAR2(100) NOT NULL
     */
    @Column(name = "LAST_NAME", nullable = false, length = 100)
    private String lastName;

    /**
     * Arabic last name. Maps to Oracle: LAST_NAME_AR NVARCHAR2(200) NOT NULL
     */
    @Column(name = "LAST_NAME_AR", nullable = false, columnDefinition = "NVARCHAR2(200)")
    private String lastNameAr;

    // ──────────────────────────────────────────────────────────────────────────
    // Personal Information
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Maps to Oracle: DATE_OF_BIRTH DATE NOT NULL
     * Stored as Oracle DATE, mapped to Java LocalDate.
     */
    @Column(name = "DATE_OF_BIRTH", nullable = false)
    private LocalDate dateOfBirth;

    /**
     * Maps to Oracle: GENDER VARCHAR2(20) NOT NULL
     * CHK_EMP_GENDER: ('MALE','FEMALE','OTHER','PREFER_NOT_TO_SAY')
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "GENDER", nullable = false, length = 20)
    private Gender gender;

    /**
     * Maps to Oracle: BLOOD_GROUP VARCHAR2(5) — nullable
     * CHK_EMP_BLOOD: ('A+','A-','B+','B-','AB+','AB-','O+','O-')
     *
     * Uses {@link BloodGroupConverter} because Oracle stores the label ("A+")
     * not the Java enum name ("A_POSITIVE").
     */
    @Convert(converter = BloodGroupConverter.class)
    @Column(name = "BLOOD_GROUP", length = 5)
    private BloodGroup bloodGroup;

    /**
     * Maps to Oracle: MARITAL_STATUS VARCHAR2(20) — nullable
     * CHK_EMP_MARITAL: ('SINGLE','MARRIED','DIVORCED','WIDOWED','SEPARATED')
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "MARITAL_STATUS", length = 20)
    private MaritalStatus maritalStatus;

    /**
     * Maps to Oracle: NATIONALITY VARCHAR2(100) — nullable
     */
    @Column(name = "NATIONALITY", length = 100)
    private String nationality;

    /**
     * Maps to Oracle: RELIGION VARCHAR2(100) — nullable
     */
    @Column(name = "RELIGION", length = 100)
    private String religion;

    /**
     * URL to the employee's profile photo (stored externally, e.g., S3/Azure Blob).
     * Maps to Oracle: PROFILE_PHOTO_URL VARCHAR2(500) — nullable
     */
    @Column(name = "PROFILE_PHOTO_URL", length = 500)
    private String profilePhotoUrl;

    // ──────────────────────────────────────────────────────────────────────────
    // Contact Information
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Maps to Oracle: PERSONAL_EMAIL VARCHAR2(200) NOT NULL UNIQUE
     */
    @Column(name = "PERSONAL_EMAIL", nullable = false, unique = true, length = 200)
    private String personalEmail;

    /**
     * Maps to Oracle: WORK_EMAIL VARCHAR2(200) NOT NULL UNIQUE
     */
    @Column(name = "WORK_EMAIL", nullable = false, unique = true, length = 200)
    private String workEmail;

    /**
     * Maps to Oracle: PERSONAL_PHONE VARCHAR2(30) — nullable
     */
    @Column(name = "PERSONAL_PHONE", length = 30)
    private String personalPhone;

    /**
     * Maps to Oracle: WORK_PHONE VARCHAR2(30) — nullable
     */
    @Column(name = "WORK_PHONE", length = 30)
    private String workPhone;

    // ──────────────────────────────────────────────────────────────────────────
    // Employment Dates
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Maps to Oracle: HIRE_DATE DATE NOT NULL
     */
    @Column(name = "HIRE_DATE", nullable = false)
    private LocalDate hireDate;

    /**
     * Maps to Oracle: PROBATION_END_DATE DATE — nullable
     * Populated when the employee is moved off probation.
     */
    @Column(name = "PROBATION_END_DATE")
    private LocalDate probationEndDate;

    /**
     * Maps to Oracle: CONFIRMATION_DATE DATE — nullable
     * The date when the employee was formally confirmed (post-probation).
     */
    @Column(name = "CONFIRMATION_DATE")
    private LocalDate confirmationDate;

    // ──────────────────────────────────────────────────────────────────────────
    // Employment Details
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Maps to Oracle: EMPLOYMENT_STATUS VARCHAR2(30) NOT NULL DEFAULT 'PROBATION'
     * CHK_EMP_STATUS: ('ACTIVE','PROBATION','NOTICE_PERIOD','TERMINATED',
     *                  'RESIGNED','RETIRED','ON_HOLD','ABSCONDED')
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "EMPLOYMENT_STATUS", nullable = false, length = 30)
    @Builder.Default
    private EmploymentStatus employmentStatus = EmploymentStatus.PROBATION;

    /**
     * Maps to Oracle: EMPLOYMENT_TYPE VARCHAR2(30) NOT NULL
     * CHK_EMP_TYPE: ('FULL_TIME','PART_TIME','CONTRACT','INTERN','CONSULTANT')
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "EMPLOYMENT_TYPE", nullable = false, length = 30)
    private EmploymentType employmentType;

    // ──────────────────────────────────────────────────────────────────────────
    // Soft Delete
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Soft-delete flag. Maps to Oracle: IS_ACTIVE NUMBER(1,0) DEFAULT 1 NOT NULL
     * CHK_EMP_IS_ACTIVE: (IS_ACTIVE IN (0, 1))
     * {@code true} → active (IS_ACTIVE = 1), {@code false} → deactivated (IS_ACTIVE = 0)
     */
    
    @Convert(converter = BooleanToNumberConverter.class)   // ✅ handles 1/0 ↔ Boolean
    @Column(name = "IS_ACTIVE", nullable = false)           // ✅ no columnDefinition needed
    @Builder.Default
    private Boolean isActive = true;

    /** Optimistic lock: bumped on every update; a stale writer gets HTTP 409. */
    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;


    // ──────────────────────────────────────────────────────────────────────────
    // Derived / Transient helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns the employee's full English name (First [Middle] Last).
     */
    @Transient
    public String getFullName() {
        StringBuilder sb = new StringBuilder(firstName);
        if (middleName != null && !middleName.isBlank()) {
            sb.append(" ").append(middleName);
        }
        return sb.append(" ").append(lastName).toString();
    }

    /**
     * Returns the employee's full Arabic name (First [Middle] Last).
     */
    @Transient
    public String getFullNameAr() {
        StringBuilder sb = new StringBuilder(firstNameAr);
        if (middleNameAr != null && !middleNameAr.isBlank()) {
            sb.append(" ").append(middleNameAr);
        }
        return sb.append(" ").append(lastNameAr).toString();
    }
}
