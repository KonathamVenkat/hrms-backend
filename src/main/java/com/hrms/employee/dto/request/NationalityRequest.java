package com.hrms.employee.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NationalityRequest {

    @NotBlank(message = "Nationality code is required")
    @Size(max = 30)
    @Pattern(regexp = "^[A-Z0-9_]+$",
             message = "Code must be uppercase letters, numbers and underscores only")
    private String code;

    @NotBlank(message = "Nationality name is required")
    @Size(max = 200)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Name contains control or invisible characters")
    private String name;

    @NotBlank(message = "Arabic nationality name is required")
    @Size(max = 200)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Arabic name contains control or invisible characters")
    private String nameAr;

    /** Optional country behind the nationality; omit to keep it, send an empty string to clear it. */
    @Size(max = 200)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Country name contains control or invisible characters")
    private String countryName;

    @Size(max = 200)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Arabic country name contains control or invisible characters")
    private String countryNameAr;

    private Boolean isActive;

    /** The version the screen was loaded with; omit to skip the stale-edit check. */
    private Long version;
}
