package com.hrms.employee.dto.response;

import lombok.*;

@Data @Builder
public class CompanyBranchResponse {
    private Long    id;
    private Long    companyId;
    private String  code;
    private String  name;
    private String  nameAr;
    private Boolean isActive;
    private String  createdAt;
    private String  updatedAt;
    private Long    version;
}
