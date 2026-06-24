package com.hrms.employee.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "DEPARTMENTS", schema = "HRMS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Department {

    @Id
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

    @Column(name = "COST_CENTER_CODE", length = 50)
    private String costCenterCode;

    @Column(name = "IS_ACTIVE")
    private Integer isActive;
}