package com.hrms.payroll.dto.request;

import com.hrms.payroll.enums.CalculationType;
import com.hrms.payroll.enums.ComponentType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// ── SalaryComponentRequest ────────────────────────────────
public record SalaryComponentRequest(
        @NotBlank(message = "Component code is required")
        @Size(max = 30)
        @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "Code must be letters, numbers and underscores only")
        String componentCode,
        @NotBlank(message = "Component name is required")
        @Size(max = 100)
        @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Name contains control or invisible characters")
        String componentName,
        @NotBlank(message = "Arabic name is required")
        @Size(max = 200)
        @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Arabic name contains control or invisible characters")
        String componentNameAr,
        @NotNull(message = "Component type is required")
        ComponentType componentType,
        @NotNull(message = "Calculation type is required")
        CalculationType calcType,
        @DecimalMin(value = "0.0")
        BigDecimal defaultValue,
        Boolean isTaxable,
        Boolean isNssfApplicable,
        @Size(max = 500)
        @Pattern(regexp = "^[[^\\p{Cc}\\p{Cf}]\\r\\n\\t]*$", message = "Description contains control or invisible characters")
        String  description,
        Integer sortOrder,
        /** The version the screen was loaded with; required to update (400 when missing, 409 when stale). */
        Long version
) {
    /** A percentage of basic or gross is a share, so it cannot exceed 100. */
    @AssertTrue(message = "A percentage cannot exceed 100")
    public boolean isPercentageWithinRange() {
        boolean percentage = calcType == CalculationType.PERCENTAGE_OF_BASIC || calcType == CalculationType.PERCENTAGE_OF_GROSS;
        return !percentage || defaultValue == null || defaultValue.compareTo(new BigDecimal("100")) <= 0;
    }
}
