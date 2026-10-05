package com.hrms.payroll.entity;

import com.hrms.common.audit.Auditable;
import com.hrms.payroll.enums.CalculationType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * A single component row within a salary structure.
 * Holds the amount or percentage override for that structure.
 *
 * Example: In "Grade A Package", TRANSPORT = 100 SSP (FIXED),
 *          HRA = 25% of BASIC (PERCENTAGE_OF_BASIC).
 */
@Entity
@Table(name = "SALARY_STRUCTURE_ITEMS", schema = "HRMS")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SalaryStructureItem extends Auditable {

    @Id
    @Column(name = "ITEM_ID", nullable = false)
    private Long itemId;

    @Column(name = "STRUCTURE_ID", nullable = false)
    private Long structureId;

    @Column(name = "COMPONENT_ID", nullable = false)
    private Long componentId;

    // Optional override of the component's default calcType
    @Enumerated(EnumType.STRING)
    @Column(name = "CALC_TYPE_OVERRIDE", length = 30)
    private CalculationType calcTypeOverride;

    @Column(name = "AMOUNT", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "PERCENTAGE", nullable = false, precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal percentage = BigDecimal.ZERO;

    @Column(name = "SORT_ORDER", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "IS_ACTIVE", nullable = false)
    @Builder.Default
    private Integer isActive = 1;

    // Read-only join to component for response building
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "COMPONENT_ID", insertable = false, updatable = false)
    private SalaryComponent component;
}
