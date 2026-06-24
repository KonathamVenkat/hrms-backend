package com.hrms.payroll.entity;

import com.hrms.common.audit.Auditable;
import com.hrms.payroll.enums.CalculationType;
import com.hrms.payroll.enums.ComponentType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Master list of salary components used across all structures.
 * Examples: Basic Salary, HRA, Transport, PASI Employee 7%, Absence Deduction.
 * Seeded with standard Oman components in V5_1 DDL.
 */
@Entity
@Table(name = "SALARY_COMPONENTS", schema = "HRMS")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SalaryComponent extends Auditable {

    @Id
    @Column(name = "COMPONENT_ID", nullable = false)
    private Long componentId;

    @Column(name = "COMPONENT_CODE", nullable = false, length = 30)
    private String componentCode;

    @Column(name = "COMPONENT_NAME", nullable = false, length = 100)
    private String componentName;

    @Column(name = "COMPONENT_NAME_AR", nullable = false, length = 200)
    private String componentNameAr;

    @Enumerated(EnumType.STRING)
    @Column(name = "COMPONENT_TYPE", nullable = false, length = 20)
    private ComponentType componentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "CALC_TYPE", nullable = false, length = 30)
    private CalculationType calcType;

    @Column(name = "DEFAULT_VALUE", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal defaultValue = BigDecimal.ZERO;

    @Column(name = "IS_TAXABLE", nullable = false)
    @Builder.Default
    private Integer isTaxable = 0;

    @Column(name = "IS_PASI_APPLICABLE", nullable = false)
    @Builder.Default
    private Integer isPasiApplicable = 0;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "SORT_ORDER", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "IS_ACTIVE", nullable = false)
    @Builder.Default
    private Integer isActive = 1;
}
