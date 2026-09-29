package com.hrms.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** HR_ADMIN setting a temporary password; the user must change it at next sign-in. */
public record ResetPasswordRequest(

    @NotNull(message = "Employee ID is required")
    Long employeeId,

    @NotBlank(message = "Temporary password is required")
    @Size(max = 200)
    String temporaryPassword

) {}
