package com.hrms.auth.service;

import com.hrms.auth.dto.request.ChangePasswordRequest;
import com.hrms.auth.dto.request.LoginRequest;
import com.hrms.auth.dto.request.LogoutRequest;
import com.hrms.auth.dto.request.RefreshTokenRequest;
import com.hrms.auth.dto.request.ResetPasswordRequest;
import com.hrms.auth.dto.response.LoginResponse;
import com.hrms.auth.dto.response.UserInfoResponse;

import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {

    /**
     * Validates credentials, issues JWT + refresh token.
     * Increments failed-attempts counter on bad password.
     * Locks account after 5 consecutive failures.
     */
    LoginResponse login(LoginRequest request, HttpServletRequest httpRequest);
    
  

    /**
     * Validates the refresh token, issues a new access token
     * (and optionally rotates the refresh token — controlled by config).
     */
    LoginResponse refreshToken(RefreshTokenRequest request, HttpServletRequest httpRequest);

    /**
     * Revokes the refresh token server-side.
     * The short-lived access token is left to expire naturally.
     */
    void logout(LogoutRequest request);

    /**
     * Validates a JWT — called by API Gateway / other microservices.
     * Returns the user info embedded in the token.
     */
    UserInfoResponse validateToken(String bearerToken);

    /**
     * The signed-in user replaces their own password. Requires the current password
     * (wrong attempts count toward lockout), enforces {@code PasswordPolicy}, clears the
     * must-change flag and revokes every refresh token so other sessions end.
     */
    void changePassword(String username, ChangePasswordRequest request);

    /**
     * HR_ADMIN sets a temporary password for an employee's account. The user is unlocked,
     * flagged to change it at next sign-in, and all their refresh tokens are revoked.
     */
    void resetPassword(ResetPasswordRequest request, String adminUsername);
}
