package com.hrms.payroll.entity;

import com.hrms.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Maps an employee to a salary structure with effective dates.
 * Supports salary revisions — old records get IS_CURRENT = 0
 * and EFFECTIVE_TO set when a new record is created.
 *
 * Payroll run always reads the record where IS_CURRENT = 1.
 */
@Entity
@Table(name = "EMPLOYEE_SALARY", schema = "HRMS")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class EmployeeSalary extends Auditable {

    @Id
    @Column(name = "EMP_SALARY_ID", nullable = false)
    private Long empSalaryId;

    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

    @Column(name = "STRUCTURE_ID", nullable = false)
    private Long structureId;

    @Column(name = "BASIC_SALARY", nullable = false, precision = 12, scale = 2)
    private BigDecimal basicSalary;

    @Column(name = "GROSS_SALARY", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossSalary;

    @Column(name = "NET_SALARY", nullable = false, precision = 12, scale = 2)
    private BigDecimal netSalary;

    @Column(name = "CURRENCY", nullable = false, length = 5)
    @Builder.Default
    private String currency = "SSP";

    @Column(name = "EFFECTIVE_FROM", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "EFFECTIVE_TO")
    private LocalDate effectiveTo;

    @Column(name = "IS_CURRENT", nullable = false)
    @Builder.Default
    private Integer isCurrent = 1;

    @Column(name = "REMARKS", length = 500)
    private String remarks;
}
