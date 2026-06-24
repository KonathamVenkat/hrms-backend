package com.hrms.employee.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DocumentTypeRequest {

    @NotBlank(message = "Document type code is required")
    @Size(max = 30)
    @Pattern(regexp = "^[A-Z0-9_]+$",
             message = "Code must be uppercase letters, numbers and underscores only")
    private String docTypeCode;

    @NotBlank(message = "Document type name is required")
    @Size(max = 100)
    private String docTypeName;

    @NotBlank(message = "Arabic document type name is required")
    @Size(max = 200)
    private String docTypeNameAr;

    @NotBlank(message = "Category is required")
    @Pattern(regexp = "^(IDENTITY|CONTRACT|EDUCATION|CERTIFICATE|MEDICAL|FINANCIAL|OTHER)$",
             message = "Invalid category")
    private String category;

    @Size(max = 500)
    private String description;

    private Boolean isMandatory;
    private Boolean hasExpiry;

    @Min(value = 0,   message = "Expiry notice days cannot be negative")
    @Max(value = 365, message = "Expiry notice days cannot exceed 365")
    private Integer expiryNoticeDays;

    @Size(max = 200)
    private String allowedExtensions;

    @Min(value = 1,  message = "Max file size must be at least 1 MB")
    @Max(value = 50, message = "Max file size cannot exceed 50 MB")
    private Integer maxFileSizeMb;

    @Min(value = 0)
    private Integer sortOrder;

    private Boolean isActive;
}

