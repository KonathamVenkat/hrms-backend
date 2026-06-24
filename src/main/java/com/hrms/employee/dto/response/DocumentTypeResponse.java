package com.hrms.employee.dto.response;

import lombok.*;

@Data @Builder
public class DocumentTypeResponse {
    private Long    docTypeId;
    private String  docTypeCode;
    private String  docTypeName;
    private String  docTypeNameAr;
    private String  category;
    private String  description;
    private Boolean isMandatory;
    private Boolean hasExpiry;
    private Integer expiryNoticeDays;
    private String  allowedExtensions;
    private Integer maxFileSizeMb;
    private Boolean isActive;
    private Integer sortOrder;
    private String  createdBy;
    private String  createdAt;
    private String  updatedAt;
}
