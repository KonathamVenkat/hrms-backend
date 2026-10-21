package com.hrms.recruitment.entity;

import com.hrms.common.audit.Auditable;
import com.hrms.common.enums.EmploymentType;
import com.hrms.recruitment.enums.RequisitionPriority;
import com.hrms.recruitment.enums.RequisitionStatus;
import com.hrms.recruitment.enums.RequisitionType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A department head's request to fill one or more positions (table JOB_REQUISITIONS).
 * Foreign keys are plain ids, like Department.headEmployeeId, so a list never lazy-loads in a loop;
 * the service resolves the names it needs in one query per kind.
 */
@Entity
@Table(name = "JOB_REQUISITIONS", schema = "HRMS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class JobRequisition extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "job_requisitions_seq")
    @SequenceGenerator(name = "job_requisitions_seq", sequenceName = "HRMS.SEQ_JOB_REQUISITIONS", allocationSize = 1)
    @Column(name = "REQUISITION_ID", nullable = false)
    private Long id;

    @Column(name = "REQUISITION_CODE", nullable = false, length = 30, updatable = false)
    private String code;

    @Column(name = "JOB_TITLE", nullable = false, length = 200)
    private String jobTitle;

    @Column(name = "JOB_TITLE_AR", columnDefinition = "NVARCHAR2(300)")
    private String jobTitleAr;

    @Column(name = "DEPARTMENT_ID", nullable = false)
    private Long departmentId;

    @Column(name = "DESIGNATION_ID", nullable = false)
    private Long designationId;

    @Column(name = "LOCATION_ID")
    private Long locationId;

    @Column(name = "REPORTING_MANAGER_ID")
    private Long reportingManagerId;

    /** The employee who raised it. Always the signed-in user, never taken from the request. */
    @Column(name = "REQUESTED_BY", nullable = false, updatable = false)
    private Long requestedBy;

    @Column(name = "NO_OF_POSITIONS", nullable = false)
    private Integer noOfPositions;

    @Column(name = "FILLED_POSITIONS", nullable = false)
    private Integer filledPositions;

    @Enumerated(EnumType.STRING)
    @Column(name = "EMPLOYMENT_TYPE", nullable = false, length = 30)
    private EmploymentType employmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "REQUISITION_TYPE", nullable = false, length = 20)
    private RequisitionType requisitionType;

    @Column(name = "REPLACED_EMPLOYEE_ID")
    private Long replacedEmployeeId;

    @Column(name = "MIN_EXPERIENCE_YEARS", nullable = false, precision = 4, scale = 1)
    private BigDecimal minExperienceYears;

    @Column(name = "MAX_EXPERIENCE_YEARS", precision = 4, scale = 1)
    private BigDecimal maxExperienceYears;

    @Column(name = "MIN_SALARY", precision = 14, scale = 3)
    private BigDecimal minSalary;

    @Column(name = "MAX_SALARY", precision = 14, scale = 3)
    private BigDecimal maxSalary;

    @Column(name = "CURRENCY", nullable = false, length = 5)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "PRIORITY", nullable = false, length = 10)
    private RequisitionPriority priority;

    @Column(name = "TARGET_JOIN_DATE")
    private LocalDate targetJoinDate;

    @Column(name = "JUSTIFICATION", length = 1000)
    private String justification;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private RequisitionStatus status;

    @Column(name = "CLOSED_AT")
    private LocalDateTime closedAt;

    /** Optimistic lock: two people acting on the same requisition no longer overwrite each other. */
    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;
}
