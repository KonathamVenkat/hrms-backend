package com.hrms.employee.dto.response;

import lombok.Builder;
import lombok.Data;

/** One row of a read-only dropdown of a master (active rows only). */
@Data @Builder
public class MasterLookupResponse {
    private Long   id;
    private String code;
    private String name;
    private String nameAr;
}
