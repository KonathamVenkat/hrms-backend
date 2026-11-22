package com.hrms.payroll.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Record an approved loan (HR_ADMIN). The instalment is worked out by the server (principal / months); status, amounts
 * repaid by payroll runs and audit fields come from the server too.
 */
public record EmployeeLoanRequest(

        @NotNull(message = "Employee is required")
        @Positive
        Long employeeId,

        /** A salary component flagged as a loan type. */
        @NotNull(message = "Loan type is required")
        @Positive
        Long componentId,

        @NotNull(message = "Principal is required")
        @DecimalMin(value = "0.01", message = "Principal must be greater than zero")
        @Digits(integer = 12, fraction = 2, message = "Principal has at most 2 decimals")
        BigDecimal principal,

        @NotNull(message = "Number of months is required")
        @Min(value = 1, message = "At least 1 month")
        @Max(value = 360, message = "At most 360 months")
        Integer instalmentCount,

        /** The first month repaid, as YYYY-MM. */
        @NotBlank(message = "First month is required")
        @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "First month must be YYYY-MM")
        String startMonth,

        /** For a loan already running when it is entered: instalments deducted before. Left out = 0. */
        @Min(value = 0, message = "Instalments already paid cannot be negative")
        Integer instalmentsPaid,

        @Size(max = 500)
        @Pattern(regexp = "^(?:[\r\n\t]|[^\\p{Cc}\\p{Cf}])*$", message = "Remarks contain control or invisible characters")
        String remarks
) {}
