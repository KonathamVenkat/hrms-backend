package com.hrms.employee.dto.response;

import lombok.Builder;
import lombok.Data;
 
@Data @Builder
public class DepartmentLookupResponse {
    private Long   id;
    private String code;
    private String name;
    private String nameAr;
    private String costCenterCode;
}