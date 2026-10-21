package com.hrms.recruitment.dto.request;

import com.hrms.recruitment.enums.StageType;
import jakarta.validation.constraints.*;

public record StageRequest(

    @NotBlank(message = "Stage code is required") @Size(max = 30)
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "Code must be uppercase letters, numbers and underscores only")
    String code,

    @NotBlank(message = "Stage name is required") @Size(max = 100)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Name contains control or invisible characters")
    String name,

    @NotBlank(message = "Arabic stage name is required") @Size(max = 200)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Arabic name contains control or invisible characters")
    String nameAr,

    @NotNull(message = "Stage type is required") StageType stageType,

    @NotNull(message = "Sort order is required") @Min(0) @Max(999) Integer sortOrder,

    Boolean isActive,

    /** The version the screen was loaded with; required to update. */
    Long version
) { }
