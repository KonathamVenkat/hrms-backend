package com.hrms.leave.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Request to manually adjust an employee's leave balance.
 * Used by HR Admin for corrections, additional grants, deductions.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AdjustBalanceRequest {

    @NotBlank(message = "Leave type code is required")
    private String leaveTypeCode;

    @NotNull(message = "Year is required")
    @Min(value = 2020) @Max(value = 2100)
    private Integer year;

    /**
     * GRANT  = add days to total
     * DEDUCT = remove days from total
     * RESET  = set total to exact value
     */
    @NotBlank(message = "Adjustment type is required")
    @Pattern(regexp = "^(GRANT|DEDUCT|RESET)$",
             message = "Must be GRANT, DEDUCT or RESET")
    private String adjustmentType;

    @NotNull(message = "Days is required")
    @DecimalMin(value = "0.5", message = "Days must be at least 0.5")
    @DecimalMax(value = "365", message = "Days cannot exceed 365")
    private Double days;

    @NotBlank(message = "Reason is required")
    @Size(max = 500, message = "Reason max 500 characters")
    private String reason;
}

