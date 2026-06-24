package com.hrms.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload for POST /api/v1/auth/refresh.
 * Angular sends: { refreshToken: string }
 */
public record RefreshTokenRequest(

    @NotBlank(message = "Refresh token is required")
    String refreshToken

) {}
