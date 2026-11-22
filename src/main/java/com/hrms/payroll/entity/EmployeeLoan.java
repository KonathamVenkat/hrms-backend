package com.hrms.payroll.entity;

import com.hrms.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A loan or advance of an employee (table EMPLOYEE_LOANS, V33): its type is a salary component flagged IS_LOAN; it is
 * repaid in INSTALMENT_COUNT monthly instalments of MONTHLY_INSTALMENT from START_MONTH (the last one takes what is
 * left after rounding). INSTALMENTS_PAID grows with each payroll run. One ACTIVE loan per employee and type.
 */
@Entity
@Table(name = "EMPLOYEE_LOANS", schema = "HRMS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmployeeLoan extends Auditable {

    @Id
    @Column(name = "LOAN_ID", nullable = false)
    private Long loanId;

    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

    @Column(name = "COMPONENT_ID", nullable = false)
    private Long componentId;

    @Column(name = "PRINCIPAL", nullable = false, precision = 14, scale = 2)
    private BigDecimal principal;

    @Column(name = "INSTALMENT_COUNT", nullable = false)
    private Integer instalmentCount;

    @Column(name = "MONTHLY_INSTALMENT", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyInstalment;

    /** Always the first day of a month. */
    @Column(name = "START_MONTH", nullable = false)
    private LocalDate startMonth;

    @Column(name = "INSTALMENTS_PAID", nullable = false)
    @Builder.Default
    private Integer instalmentsPaid = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    @Builder.Default
    private LoanStatus status = LoanStatus.ACTIVE;

    @Column(name = "STATUS_REASON", length = 500)
    private String statusReason;

    @Column(name = "STATUS_CHANGED_BY", length = 100)
    private String statusChangedBy;

    @Column(name = "STATUS_CHANGED_AT")
    private LocalDateTime statusChangedAt;

    @Column(name = "REMARKS", length = 500)
    private String remarks;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;

    public enum LoanStatus { ACTIVE, CLOSED, CANCELLED }
}
