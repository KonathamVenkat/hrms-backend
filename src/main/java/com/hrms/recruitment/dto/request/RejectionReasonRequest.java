package com.hrms.recruitment.dto.request;

import com.hrms.recruitment.enums.ReasonScope;
import jakarta.validation.constraints.*;

public record RejectionReasonRequest(

    @NotBlank(message = "Reason code is required") @Size(max = 30)
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "Code must be uppercase letters, numbers and underscores only")
    String code,

    @NotBlank(message = "Reason name is required") @Size(max = 150)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Name contains control or invisible characters")
    String name,

    @NotBlank(message = "Arabic reason name is required") @Size(max = 300)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Arabic name contains control or invisible characters")
    String nameAr,

    @NotNull(message = "Applies-to is required") ReasonScope appliesTo,

    @NotNull(message = "Sort order is required") @Min(0) @Max(999) Integer sortOrder,

    Boolean isActive,

    /** The version the screen was loaded with; required to update. */
    Long version
) { }
