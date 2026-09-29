package com.hrms.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The signed-in user changing their own password. */
public record ChangePasswordRequest(

    @NotBlank(message = "Current password is required")
    @Size(max = 200)
    String currentPassword,

    @NotBlank(message = "New password is required")
    @Size(max = 200)
    String newPassword

) {}
