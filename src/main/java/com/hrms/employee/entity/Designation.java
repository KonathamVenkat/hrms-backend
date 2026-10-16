package com.hrms.employee.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "DESIGNATIONS", schema = "HRMS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Designation {

    // The BEFORE INSERT trigger only fills DESIG_ID when it is null, so supplying it here is safe.
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "designations_seq")
    @SequenceGenerator(name = "designations_seq", sequenceName = "HRMS.SEQ_DESIGNATIONS", allocationSize = 1)
    @Column(name = "DESIG_ID", nullable = false)
    private Long desigId;

    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

    @Column(name = "TITLE_AR", columnDefinition = "NVARCHAR2(200)")
    private String titleAr;

    @Column(name = "CODE", nullable = false, length = 30)
    private String code;

    @Column(name = "GRADE_LEVEL", length = 10)
    private String gradeLevel;

    @Column(name = "DEPARTMENT_ID")
    private Long departmentId;

    @Column(name = "IS_ACTIVE")
    private Integer isActive;

    // CREATED_AT is stamped by the insert trigger, so it is never written from here.
    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", insertable = false)
    private LocalDateTime updatedAt;
}
