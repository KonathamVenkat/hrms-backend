package com.hrms.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Maps 1-to-1 with Angular LoginRequest:
 *   { username: string; password: string }
 */
public record LoginRequest(

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be 3–50 characters")
    String username,

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 128, message = "Password must be 6–128 characters")
    String password

) {}


