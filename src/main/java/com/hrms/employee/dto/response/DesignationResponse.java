package com.hrms.employee.dto.response;

import lombok.*;

@Data @Builder
public class DesignationResponse {
    private Long    desigId;
    private String  code;
    private String  title;
    private String  titleAr;
    private String  gradeLevel;
    private Long    departmentId;
    private String  departmentName;
    private Boolean isActive;
    private String  createdAt;
    private String  updatedAt;
    private Long    version;
}
