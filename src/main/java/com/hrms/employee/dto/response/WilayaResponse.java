package com.hrms.employee.dto.response;

import lombok.*;

@Data @Builder
public class WilayaResponse {
    private Long    id;
    private Long    regionId;
    private String  code;
    private String  name;
    private String  nameAr;
    private Boolean isActive;
    private String  createdAt;
    private String  updatedAt;
    private Long    version;
}
