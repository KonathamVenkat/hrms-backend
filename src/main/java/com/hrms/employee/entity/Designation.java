package com.hrms.employee.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "DESIGNATIONS", schema = "HRMS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Designation {

    @Id
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
}