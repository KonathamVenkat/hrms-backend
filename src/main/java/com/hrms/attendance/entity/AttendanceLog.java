package com.hrms.attendance.entity;

import com.hrms.attendance.enums.AttendanceStatus;
import com.hrms.attendance.enums.PunchSource;
import com.hrms.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "ATTENDANCE_LOGS", schema = "HRMS",
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_ATT_EMP_DATE",
                columnNames = {"EMPLOYEE_ID", "ATTENDANCE_DATE"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AttendanceLog extends Auditable {

    @Id
    @Column(name = "LOG_ID", nullable = false)
    private Long logId;

    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

    @Column(name = "EMPLOYEE_CODE", nullable = false, length = 30)
    private String employeeCode;

    @Column(name = "ATTENDANCE_DATE", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "CHECK_IN_TIME")
    private LocalDateTime checkInTime;

    @Column(name = "CHECK_OUT_TIME")
    private LocalDateTime checkOutTime;

    @Column(name = "WORKING_MINUTES")
    @Builder.Default
    private Integer workingMinutes = 0;

    @Column(name = "OVERTIME_MINUTES")
    @Builder.Default
    private Integer overtimeMinutes = 0;

    @Column(name = "LATE_MINUTES")
    @Builder.Default
    private Integer lateMinutes = 0;

    @Column(name = "EARLY_LEAVE_MINUTES")
    @Builder.Default
    private Integer earlyLeaveMinutes = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private AttendanceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "PUNCH_SOURCE", length = 20)
    @Builder.Default
    private PunchSource punchSource = PunchSource.WEB;

    @Column(name = "LOCATION_ID")
    private Long locationId;

    @Column(name = "NOTES", length = 500)
    private String notes;

    @Column(name = "IS_REGULARIZED", nullable = false)
    @Builder.Default
    private Integer isRegularized = 0;

    @Column(name = "IS_ACTIVE", nullable = false)
    @Builder.Default
    private Integer isActive = 1;
}
