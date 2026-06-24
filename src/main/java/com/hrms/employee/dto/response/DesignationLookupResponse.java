package com.hrms.employee.dto.response;

import lombok.Builder;
import lombok.Data;
 
@Data @Builder
public class DesignationLookupResponse {
    private Long   id;
    private String code;
    private String title;
    private String titleAr;
    private String gradeLevel;
    private Long   departmentId;
}