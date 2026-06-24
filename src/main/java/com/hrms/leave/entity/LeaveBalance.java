package com.hrms.leave.entity;

import com.hrms.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name   = "LEAVE_BALANCES",
    schema = "HRMS",
    uniqueConstraints = @UniqueConstraint(
        name = "UQ_LEAVE_BAL",
        columnNames = {"EMPLOYEE_ID", "LEAVE_TYPE", "YEAR"}
    )
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class LeaveBalance extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "leave_bal_seq")
    @SequenceGenerator(name = "leave_bal_seq", sequenceName = "HRMS.LEAVE_BAL_SEQ", allocationSize = 1)
    @Column(name = "BALANCE_ID")
    private Long balanceId;

    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

    @Column(name = "LEAVE_TYPE", nullable = false, length = 20)
    private String leaveTypeCode;

    @Column(name = "YEAR", nullable = false)
    private Integer year;

    @Column(name = "TOTAL_DAYS", nullable = false)
    private Double totalDays;

    @Column(name = "USED_DAYS", nullable = false)
    @Builder.Default
    private Double usedDays = 0.0;

    @Column(name = "PENDING_DAYS", nullable = false)
    @Builder.Default
    private Double pendingDays = 0.0;

    @Transient
    public Double getAvailableDays() {
        return totalDays - usedDays - pendingDays;
    }
}
