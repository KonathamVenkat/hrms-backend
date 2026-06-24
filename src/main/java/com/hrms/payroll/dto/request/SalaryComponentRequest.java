package com.hrms.payroll.dto.request;

import com.hrms.payroll.enums.CalculationType;
import com.hrms.payroll.enums.ComponentType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

// ── SalaryComponentRequest ────────────────────────────────
public record SalaryComponentRequest(

        @NotBlank(message = "Component code is required")
        @Size(max = 30)
        String componentCode,

        @NotBlank(message = "Component name is required")
        @Size(max = 100)
        String componentName,

        @NotBlank(message = "Arabic name is required")
        @Size(max = 200)
        String componentNameAr,

        @NotNull(message = "Component type is required")
        ComponentType componentType,

        @NotNull(message = "Calculation type is required")
        CalculationType calcType,

        @DecimalMin(value = "0.0")
        BigDecimal defaultValue,

        Boolean isTaxable,
        Boolean isPasiApplicable,
        String  description,
        Integer sortOrder
) {}
