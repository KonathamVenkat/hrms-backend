package com.hrms.employee.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EmployeeDocumentRequest {

    @NotNull(message = "Document type is required")
    private Long docTypeId;

    @NotBlank(message = "Document name is required")
    @Size(max = 300, message = "Document name max 300 characters")
    private String documentName;

    @Size(max = 100, message = "Document number max 100 characters")
    private String documentNumber;

    private LocalDate issueDate;
    private LocalDate expiryDate;

    @Size(max = 200, message = "Issued by max 200 characters")
    private String issuedBy;

    @Size(max = 500, message = "Notes max 500 characters")
    private String notes;
}