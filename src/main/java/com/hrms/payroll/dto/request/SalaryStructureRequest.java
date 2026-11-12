package com.hrms.payroll.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SalaryStructureRequest(

        @NotBlank(message = "Structure code is required")
        @Size(max = 30)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Code must be letters, numbers, hyphens and underscores only")
        String structureCode,

        @NotBlank(message = "Structure name is required")
        @Size(max = 100)
        @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Name contains control or invisible characters")
        String structureName,

        @Size(max = 500)
        @Pattern(regexp = "^[[^\\p{Cc}\\p{Cf}]\\r\\n\\t]*$", message = "Description contains control or invisible characters")
        String description,

        @NotEmpty(message = "At least one salary component is required")
        @Size(max = 100, message = "A structure can hold at most 100 components")
        @Valid
        List<SalaryStructureItemRequest> items,
        /** The version the screen was loaded with; required to update (400 when missing, 409 when stale). */
        Long version
) {}
