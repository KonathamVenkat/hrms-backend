package com.hrms.payroll.entity;

import com.hrms.common.util.BooleanToNumberConverter;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Qualification level master (table QUALIFICATION_LEVELS, V32): a level of the Qualification Allowance and its monthly
 * amount. Columns carry the table name as prefix, the Java fields stay plain (like Grade).
 */
@Entity
@Table(name = "QUALIFICATION_LEVELS", schema = "HRMS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QualificationLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "qualification_levels_seq")
    @SequenceGenerator(name = "qualification_levels_seq", sequenceName = "HRMS.SEQ_QUALIFICATION_LEVELS", allocationSize = 1)
    @Column(name = "QUALIFICATION_LEVEL_ID", nullable = false)
    private Long id;

    @Column(name = "QUALIFICATION_LEVELS_CODE", nullable = false, length = 30)
    private String code;

    @Column(name = "QUALIFICATION_LEVELS_NAME", nullable = false, length = 200)
    private String name;

    @Column(name = "QUALIFICATION_LEVELS_NAME_AR", nullable = false, columnDefinition = "NVARCHAR2(200)")
    private String nameAr;

    /** Paid every month to an employee on this level; more than 0, 2 decimals. */
    @Column(name = "MONTHLY_AMOUNT", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyAmount;

    @Column(name = "SORT_ORDER")
    private Integer sortOrder;

    @Convert(converter = BooleanToNumberConverter.class)
    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean isActive;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;

    // Both timestamps default to SYSTIMESTAMP in the table; the service sets UPDATED_AT on change.
    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", insertable = false)
    private LocalDateTime updatedAt;
}
