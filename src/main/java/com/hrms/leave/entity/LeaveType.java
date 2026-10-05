package com.hrms.leave.entity;


import com.hrms.common.enums.Gender;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA Entity for HRMS.LEAVE_TYPES table.
 * Configurable leave types managed by HR Admin.
 */
@Entity
@Table(
    name   = "LEAVE_TYPES",
    schema = "HRMS",
    uniqueConstraints = {
        @UniqueConstraint(name = "UQ_LEAVE_TYPE_CODE", columnNames = "CODE"),
        @UniqueConstraint(name = "UQ_LEAVE_TYPE_NAME", columnNames = "NAME_EN")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LeaveType {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "leave_type_seq")
    @SequenceGenerator(
        name           = "leave_type_seq",
        sequenceName   = "HRMS.SEQ_LEAVE_TYPES",
        allocationSize = 1
    )
    @Column(name = "LEAVE_TYPE_ID", nullable = false)
    private Long leaveTypeId;

    @Column(name = "CODE", nullable = false, length = 30)
    private String code;

    @Column(name = "NAME_EN", nullable = false, length = 100)
    private String nameEn;

    @Column(name = "NAME_AR", nullable = false, columnDefinition = "NVARCHAR2(200)")
    private String nameAr;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "DEFAULT_DAYS", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal defaultDays = BigDecimal.ZERO;

    @Column(name = "IS_PAID", nullable = false)
    @Builder.Default
    private Integer isPaid = 1;

    @Column(name = "IS_CARRY_FORWARD", nullable = false)
    @Builder.Default
    private Integer isCarryForward = 0;

    @Column(name = "MAX_CARRY_DAYS", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal maxCarryDays = BigDecimal.ZERO;

    @Column(name = "REQUIRES_DOCUMENT", nullable = false)
    @Builder.Default
    private Integer requiresDocument = 0;

    /** Upload limits for the supporting document; NULL = defaults (see resolve* below). */
    @Column(name = "DOC_MAX_FILE_SIZE_MB")
    private Integer docMaxFileSizeMb;

    /** Comma-separated lowercase extensions, e.g. "pdf,jpg,png". */
    @Column(name = "DOC_ALLOWED_EXTENSIONS", length = 100)
    private String docAllowedExtensions;

    @Column(name = "MIN_NOTICE_DAYS", nullable = false)
    @Builder.Default
    private Integer minNoticeDays = 0;

    @Column(name = "MAX_CONSECUTIVE_DAYS", nullable = false)
    @Builder.Default
    private Integer maxConsecutiveDays = 0;

    @Column(name = "APPLICABLE_GENDER", nullable = false, length = 20)
    @Builder.Default
    private String applicableGender = "ALL";

    public static final int DEFAULT_DOC_MAX_FILE_SIZE_MB = 5;
    public static final String DEFAULT_DOC_ALLOWED_EXTENSIONS = "pdf,jpg,jpeg,png";

    public int resolveDocMaxFileSizeMb() {
        return docMaxFileSizeMb != null && docMaxFileSizeMb > 0
            ? docMaxFileSizeMb : DEFAULT_DOC_MAX_FILE_SIZE_MB;
    }

    public String resolveDocAllowedExtensions() {
        return docAllowedExtensions != null && !docAllowedExtensions.isBlank()
            ? docAllowedExtensions : DEFAULT_DOC_ALLOWED_EXTENSIONS;
    }

    @Column(name = "IS_ACTIVE", nullable = false)
    @Builder.Default
    private Integer isActive = 1;

    @Column(name = "SORT_ORDER", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "CREATED_BY", length = 50)
    private String createdBy;

    @Column(name = "CREATED_AT", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "UPDATED_BY", length = 50)
    private String updatedBy;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    /**
     * True when an employee of {@code gender} may take this leave type: a type for "ALL" (or with no
     * restriction set) is open to everyone, a restricted type needs a known, matching gender.
     */
    public boolean isApplicableTo(Gender gender) {
        if (applicableGender == null || "ALL".equalsIgnoreCase(applicableGender)) {
            return true;
        }
        return gender != null && gender.name().equalsIgnoreCase(applicableGender);
    }
}