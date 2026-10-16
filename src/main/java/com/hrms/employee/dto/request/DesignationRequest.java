package com.hrms.employee.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DesignationRequest {

    @NotBlank(message = "Designation code is required")
    @Size(max = 30)
    @Pattern(regexp = "^[A-Z0-9_]+$",
             message = "Code must be uppercase letters, numbers and underscores only")
    private String code;

    @NotBlank(message = "Designation title is required")
    @Size(max = 200)
    private String title;

    @Size(max = 200)
    private String titleAr;

    /** Free-form grade such as E1, M1 or P3. */
    @Size(max = 10)
    private String gradeLevel;

    /** Optional; must be an existing department. */
    private Long departmentId;

    private Boolean isActive;
}
