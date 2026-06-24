package com.hrms.payroll.dto.request;

import com.hrms.payroll.enums.CalculationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

// ── SalaryStructureItemRequest (nested inside structure) ──
public record SalaryStructureItemRequest(

        @NotNull(message = "Component ID is required")
        Long componentId,

        CalculationType calcTypeOverride,   // null = use component default

        @DecimalMin(value = "0.0", message = "Amount cannot be negative")
        BigDecimal amount,

        @DecimalMin(value = "0.0")
        @DecimalMax(value = "100.0", message = "Percentage cannot exceed 100")
        BigDecimal percentage,

        Integer sortOrder
) {}
