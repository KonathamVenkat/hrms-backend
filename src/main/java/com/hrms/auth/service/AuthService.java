package com.hrms.auth.service;

import com.hrms.auth.dto.*;
import com.hrms.auth.dto.request.LoginRequest;
import com.hrms.auth.dto.request.LogoutRequest;
import com.hrms.auth.dto.request.RefreshTokenRequest;
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
}
