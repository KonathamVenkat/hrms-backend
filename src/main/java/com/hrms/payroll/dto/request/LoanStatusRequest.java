package com.hrms.payroll.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Close (repaid early) or cancel (entered by mistake) a loan: the version it was shown with, and why. */
public record LoanStatusRequest(

        @NotNull(message = "version is required")
        Long version,

        @NotBlank(message = "A reason is required")
        @Size(max = 500)
        @Pattern(regexp = "^(?:[\r\n\t]|[^\\p{Cc}\\p{Cf}])*$", message = "Reason contains control or invisible characters")
        String reason
) {}
