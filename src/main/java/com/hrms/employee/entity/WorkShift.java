package com.hrms.employee.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA Entity for HRMS.WORK_SHIFTS table.
 * Defines shift timings used in Employee Job Details
 * and Attendance tracking.
 */
@Entity
@Table(
    name   = "WORK_SHIFTS",
    schema = "HRMS",
    uniqueConstraints = {
        @UniqueConstraint(name = "UQ_SHIFT_CODE", columnNames = "SHIFT_CODE"),
        @UniqueConstraint(name = "UQ_SHIFT_NAME", columnNames = "SHIFT_NAME")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WorkShift {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "work_shift_seq")
    @SequenceGenerator(
        name           = "work_shift_seq",
        sequenceName   = "HRMS.SEQ_WORK_SHIFTS",
        allocationSize = 1
    )
    @Column(name = "SHIFT_ID", nullable = false)
    private Long shiftId;

    /**
     * Unique uppercase code e.g. MORNING, NIGHT, FLEXIBLE
     */
    @Column(name = "SHIFT_CODE", nullable = false, length = 20)
    private String shiftCode;

    @Column(name = "SHIFT_NAME", nullable = false, length = 100)
    private String shiftName;

    @Column(name = "SHIFT_NAME_AR", nullable = false,
            columnDefinition = "NVARCHAR2(200)")
    private String shiftNameAr;

    /**
     * MORNING | AFTERNOON | EVENING | NIGHT | FLEXIBLE | SPLIT
     */
    @Column(name = "SHIFT_TYPE", nullable = false, length = 20)
    @Builder.Default
    private String shiftType = "MORNING";

    /** Start time in HH:MM (24-hour) format */
    @Column(name = "START_TIME", nullable = false, length = 5)
    private String startTime;

    /** End time in HH:MM (24-hour) format */
    @Column(name = "END_TIME", nullable = false, length = 5)
    private String endTime;

    /** Break duration in minutes */
    @Column(name = "BREAK_DURATION", nullable = false)
    @Builder.Default
    private Integer breakDuration = 0;

    /** Net working hours after break */
    @Column(name = "WORKING_HOURS", nullable = false, precision = 4, scale = 2)
    private BigDecimal workingHours;
    /** Late arrival tolerance in minutes */
    @Column(name = "GRACE_PERIOD", nullable = false)
    @Builder.Default
    private Integer gracePeriod = 0;

    /** Comma-separated working days e.g. SUN,MON,TUE,WED,THU */
    @Column(name = "WORKING_DAYS", nullable = false, length = 100)
    private String workingDays;

    /** True if shift crosses midnight (e.g. 22:00 – 06:00) */
    @Column(name = "IS_OVERNIGHT", nullable = false)
    @Builder.Default
    private Integer isOvernight = 0;

    /** True if no fixed start/end time — employee logs 8hrs */
    @Column(name = "IS_FLEXIBLE", nullable = false)
    @Builder.Default
    private Integer isFlexible = 0;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

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
}
