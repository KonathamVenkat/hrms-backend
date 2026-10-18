package com.hrms.employee.entity;

import com.hrms.common.audit.Auditable;
import com.hrms.common.util.BooleanToNumberConverter;
import jakarta.persistence.*;
import lombok.*;

/**
 * Bank account of an employee (table EMPLOYEE_BANK_ACCOUNTS, created by V5).
 *
 * An employee can hold several accounts; at most one active account is the primary one (unique index
 * UQ_EBA_ONE_PRIMARY) and payroll pays into it. A bank account is never deleted, only deactivated.
 * The account number and IBAN are sensitive: responses mask them unless the caller is HR_ADMIN or the employee.
 */
@Entity
@Table(name = "EMPLOYEE_BANK_ACCOUNTS", schema = "HRMS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmployeeBankAccount extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "emp_bank_account_seq")
    @SequenceGenerator(name = "emp_bank_account_seq", sequenceName = "HRMS.SEQ_EMPLOYEE_BANK_ACCOUNTS",
        allocationSize = 1)
    @Column(name = "BANK_ACCOUNT_ID", nullable = false)
    private Long id;

    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

    @Column(name = "BANK_ID", nullable = false)
    private Long bankId;

    /** Optional; when set it belongs to {@link #bankId}. */
    @Column(name = "BANK_BRANCH_ID")
    private Long bankBranchId;

    @Column(name = "ACCOUNT_NUMBER", nullable = false, length = 40)
    private String accountNumber;

    @Column(name = "IBAN", length = 50)
    private String iban;

    @Column(name = "ACCOUNT_HOLDER_NAME", length = 200)
    private String accountHolderName;

    @Convert(converter = BooleanToNumberConverter.class)
    @Column(name = "IS_PRIMARY", nullable = false)
    private Boolean isPrimary;

    @Convert(converter = BooleanToNumberConverter.class)
    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean isActive;

    /** Optimistic lock: a stale writer gets HTTP 409. */
    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;
}
