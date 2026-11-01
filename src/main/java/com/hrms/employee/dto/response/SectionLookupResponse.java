package com.hrms.employee.dto.response;

import lombok.*;

/** One active section for a dropdown. */
@Data @Builder
public class SectionLookupResponse {
    private Long   id;
    private String code;
    private String name;
    private String nameAr;
    private Long   departmentId;
}
