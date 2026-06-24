package com.hrms.leave.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.util.List;

/**
 * Request to initialize leave balances for one or all employees
 * for a given year. Used by HR Admin at start of year.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class InitializeBalancesRequest {

    @NotNull(message = "Year is required")
    @Min(value = 2020, message = "Year must be 2020 or later")
    @Max(value = 2100, message = "Year must be 2100 or earlier")
    private Integer year;

    /**
     * If null or empty → initialize for ALL active employees.
     * If provided → initialize only for these employee IDs.
     */
    private List<Long> employeeIds;

    /**
     * If true → skip employees who already have balances for this year.
     * If false → overwrite existing balances (use with caution).
     */
    @Builder.Default
    private Boolean skipExisting = true;

    /**
     * If true → carry forward unused annual leave from previous year.
     */
    @Builder.Default
    private Boolean applyCarryForward = true;
}
