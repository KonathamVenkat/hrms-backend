package com.hrms.payroll.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/** Create or update a qualification level (SYSTEM_ADMIN). Same shape as the other masters, plus the monthly amount. */
public record QualificationLevelRequest(

        @NotBlank(message = "Code is required")
        @Size(max = 30)
        @Pattern(regexp = "^[A-Z0-9_]+$", message = "Code must be uppercase letters, numbers and underscores only")
        String code,

        @NotBlank(message = "Name is required")
        @Size(max = 200)
        @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Name contains control or invisible characters")
        String name,

        @NotBlank(message = "Arabic name is required")
        @Size(max = 200)
        @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Arabic name contains control or invisible characters")
        String nameAr,

        @NotNull(message = "Monthly amount is required")
        @DecimalMin(value = "0.01", message = "Monthly amount must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "Monthly amount has at most 2 decimals")
        BigDecimal monthlyAmount,

        @Min(0) @Max(9999)
        Integer sortOrder,

        Boolean isActive,

        /** The version the screen was loaded with; required to update. */
        Long version
) {}
