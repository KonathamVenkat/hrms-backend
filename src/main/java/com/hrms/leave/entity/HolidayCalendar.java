package com.hrms.leave.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA Entity for HRMS.HOLIDAY_CALENDAR table.
 */
@Entity
@Table(name = "HOLIDAY_CALENDAR", schema = "HRMS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HolidayCalendar {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
                    generator = "holiday_seq")
    @SequenceGenerator(
        name         = "holiday_seq",
        sequenceName = "HRMS.SEQ_HOLIDAY_CALENDAR",
        allocationSize = 1)
    @Column(name = "HOLIDAY_ID")
    private Long holidayId;

    @Column(name = "HOLIDAY_NAME", nullable = false, length = 200)
    private String holidayName;

    @Column(name = "HOLIDAY_NAME_AR", length = 200)
    private String holidayNameAr;

    @Column(name = "HOLIDAY_DATE", nullable = false)
    private LocalDate holidayDate;

    @Column(name = "HOLIDAY_TYPE", nullable = false, length = 20)
    @Builder.Default
    private String holidayType = "PUBLIC";  // PUBLIC|RELIGIOUS|OPTIONAL|RESTRICTED

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "IS_RECURRING", nullable = false)
    @Builder.Default
    private Integer isRecurring = 0;

    @Column(name = "YEAR", nullable = false)
    private Integer year;

    @Column(name = "IS_ACTIVE", nullable = false)
    @Builder.Default
    private Integer isActive = 1;

    @Column(name = "CREATED_BY", length = 50)
    @Builder.Default
    private String createdBy = "SYSTEM";

    @Column(name = "CREATED_AT", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "UPDATED_BY", length = 50)
    private String updatedBy;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;
}
