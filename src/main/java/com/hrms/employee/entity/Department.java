package com.hrms.employee.entity;

import com.hrms.common.util.BooleanToNumberConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "DEPARTMENTS", schema = "HRMS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Department {

    // The BEFORE INSERT trigger only fills DEPT_ID when it is null, so supplying it here is safe.
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "departments_seq")
    @SequenceGenerator(name = "departments_seq", sequenceName = "HRMS.SEQ_DEPARTMENTS", allocationSize = 1)
    @Column(name = "DEPT_ID", nullable = false)
    private Long deptId;

    @Column(name = "CODE", nullable = false, length = 20)
    private String code;

    @Column(name = "NAME", nullable = false, length = 200)
    private String name;

    @Column(name = "NAME_AR", columnDefinition = "NVARCHAR2(200)")
    private String nameAr;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "HEAD_EMPLOYEE_ID")
    private Long headEmployeeId;

    @Column(name = "COST_CENTER_CODE", length = 50)
    private String costCenterCode;

    @Convert(converter = BooleanToNumberConverter.class)
    @Column(name = "IS_ACTIVE")
    private Boolean isActive;

    /** Optimistic lock: two admins saving the same row no longer overwrite each other. */
    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;

    // CREATED_AT is stamped by the insert trigger, so it is never written from here.
    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", insertable = false)
    private LocalDateTime updatedAt;
}
