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

        @NotNull(message = "Basic salary is required")
        @DecimalMin(value = "0.01", message = "Basic salary must be greater than zero")
        BigDecimal basicSalary,

        @NotNull(message = "Effective from date is required")
        String effectiveFrom,   // "yyyy-MM-dd"

        @Size(max = 500)
        String remarks
) {}
