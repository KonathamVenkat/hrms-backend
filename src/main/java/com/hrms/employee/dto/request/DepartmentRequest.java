package com.hrms.employee.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DepartmentRequest {

    @NotBlank(message = "Department code is required")
    @Size(max = 20)
    @Pattern(regexp = "^[A-Z0-9_]+$",
             message = "Code must be uppercase letters, numbers and underscores only")
    private String code;

    @NotBlank(message = "Department name is required")
    @Size(max = 200)
    private String name;

    @NotBlank(message = "Arabic department name is required")
    @Size(max = 200)
    private String nameAr;

    @Size(max = 500)
    private String description;

    /** Optional; must be an existing employee. */
    private Long headEmployeeId;

    @Size(max = 50)
    private String costCenterCode;

    private Boolean isActive;
}
