package com.hrms.employee.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Data @Builder
public class GradeResponse {
    private Long    id;
    private Integer sortOrder;
    private String  code;
    private String  name;
    private String  nameAr;
    private BigDecimal minSalary;
    private BigDecimal maxSalary;
    private Boolean isActive;
    private String  createdAt;
    private String  updatedAt;
    private Long    version;
}
