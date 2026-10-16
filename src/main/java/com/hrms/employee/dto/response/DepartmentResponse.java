package com.hrms.employee.dto.response;

import lombok.*;

@Data @Builder
public class DepartmentResponse {
    private Long    deptId;
    private String  code;
    private String  name;
    private String  nameAr;
    private String  description;
    private Long    headEmployeeId;
    private String  costCenterCode;
    private Boolean isActive;
    private String  createdAt;
    private String  updatedAt;
}
