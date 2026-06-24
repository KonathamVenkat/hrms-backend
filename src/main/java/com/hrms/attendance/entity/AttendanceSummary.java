package com.hrms.attendance.entity;

import com.hrms.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ATTENDANCE_SUMMARY", schema = "HRMS",
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_ATT_SUMMARY_EMP_YM",
                columnNames = {"EMPLOYEE_ID", "SUMMARY_YEAR", "SUMMARY_MONTH"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AttendanceSummary extends Auditable {

    @Id
    @Column(name = "SUMMARY_ID", nullable = false)
    private Long summaryId;

    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

    @Column(name = "EMPLOYEE_CODE", nullable = false, length = 30)
    private String employeeCode;

    @Column(name = "SUMMARY_YEAR", nullable = false)
    private Integer summaryYear;

    @Column(name = "SUMMARY_MONTH", nullable = false)
    private Integer summaryMonth;

    // ── Day counters — Oracle NUMBER(5,2) → BigDecimal ────────
    @Column(name = "PRESENT_DAYS", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal presentDays = BigDecimal.ZERO;

    @Column(name = "ABSENT_DAYS", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal absentDays = BigDecimal.ZERO;

    @Column(name = "HALF_DAYS", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal halfDays = BigDecimal.ZERO;

    @Column(name = "LATE_DAYS", nullable = false)
    @Builder.Default
    private Integer lateDays = 0;

    @Column(name = "LEAVE_DAYS", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal leaveDays = BigDecimal.ZERO;

    @Column(name = "HOLIDAY_DAYS", nullable = false)
    @Builder.Default
    private Integer holidayDays = 0;

    @Column(name = "WEEKEND_DAYS", nullable = false)
    @Builder.Default
    private Integer weekendDays = 0;

    // ── Minute accumulators — Oracle NUMBER(8,0) → Long ───────
    @Column(name = "TOTAL_WORKING_MINS", nullable = false)
    @Builder.Default
    private Long totalWorkingMins = 0L;

    @Column(name = "TOTAL_OVERTIME_MINS", nullable = false)
    @Builder.Default
    private Long totalOvertimeMins = 0L;

    @Column(name = "TOTAL_LATE_MINS", nullable = false)
    @Builder.Default
    private Long totalLateMins = 0L;

    @Column(name = "LAST_CALCULATED_AT", nullable = false)
    @Builder.Default
    private LocalDateTime lastCalculatedAt = LocalDateTime.now();
}