package com.hrms.employee.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SectionRequest {

    @NotNull(message = "Department is required")
    private Long departmentId;

    @NotBlank(message = "Section code is required")
    @Size(max = 30)
    @Pattern(regexp = "^[A-Z0-9_-]+$",
             message = "Code must be uppercase letters, numbers, hyphens and underscores only")
    private String code;

    @NotBlank(message = "Section name is required")
    @Size(max = 200)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Name contains control or invisible characters")
    private String name;

    @NotBlank(message = "Arabic section name is required")
    @Size(max = 200)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Arabic name contains control or invisible characters")
    private String nameAr;

    private Boolean isActive;

    /** The version the screen was loaded with; omit to skip the stale-edit check. */
    private Long version;
}
