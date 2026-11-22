package com.hrms.payroll.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record EmployeeSalaryRequest(

        @NotNull(message = "Employee ID is required")
        Long employeeId,

        @NotNull(message = "Structure ID is required")
        Long structureId,

        // Required for an employee without a grade; for a graded one it comes from the pay scale (may be left out,
        // and is refused when it differs). Checked in the service, which knows which case applies.
        @DecimalMin(value = "0.01", message = "Basic salary must be greater than zero")
        BigDecimal basicSalary,

        @NotNull(message = "Effective from date is required")
        String effectiveFrom,   // "yyyy-MM-dd"

        @Size(max = 500)
        String remarks,

        // Per-employee allowances, part of this salary (step 4a). Social: true pays the Social Allowance component's
        // default amount. Qualification: an active level, or null for none. Left out = not paid.
        Boolean socialAllowance,
        Long qualificationLevelId
) {}
