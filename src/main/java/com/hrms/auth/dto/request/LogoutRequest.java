package com.hrms.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/** POST /api/v1/auth/logout — revokes the supplied refresh token. */
public record LogoutRequest(

    @NotBlank(message = "Token is required")
    String token

) {}
