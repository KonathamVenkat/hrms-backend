package com.hrms.auth.controller;

import com.hrms.auth.dto.request.*;
import com.hrms.auth.dto.response.*;
import com.hrms.auth.service.AuthService;
import com.hrms.auth.security.RefreshCookie;
import com.hrms.common.dto.ApiResponse;
import com.hrms.common.exception.InvalidTokenException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "JWT Authentication and Authorisation endpoints")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookie refreshCookie;

    // ── POST /api/v1/auth/login ───────────────────────────────────────────
    @Operation(
        summary     = "Login",
        description = "Validates credentials and returns JWT access + refresh tokens.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Login successful"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Invalid credentials"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Account locked or disabled"),
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(   // ✅ wrapped
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        log.info("Login attempt — identifier={}", request.username());
        LoginResponse response = authService.login(request, httpRequest);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshCookie.create(response.refreshToken()))
            .body(ApiResponse.success("Login successful", response.withoutRefreshToken()));
    }

    // ── POST /api/v1/auth/refresh ─────────────────────────────────────────
    @Operation(
        summary     = "Refresh access token",
        description = "Exchanges the refresh token (sent as an HttpOnly cookie) for a new access token "
                    + "and sets the replacement refresh cookie.")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(HttpServletRequest httpRequest) {

        log.debug("Token refresh request received");
        String token = refreshCookie.read(httpRequest)
            .orElseThrow(() -> new InvalidTokenException("Refresh token not found."));
        LoginResponse response = authService.refreshToken(new RefreshTokenRequest(token), httpRequest);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshCookie.create(response.refreshToken()))
            .body(ApiResponse.success("Token refreshed", response.withoutRefreshToken()));
    }

    // ── POST /api/v1/auth/logout ──────────────────────────────────────────
    @Operation(
        summary     = "Logout",
        description = "Revokes the refresh token (from the HttpOnly cookie) server-side and removes the cookie.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest httpRequest) {

        refreshCookie.read(httpRequest).ifPresent(token -> authService.logout(new LogoutRequest(token)));
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshCookie.clear())
            .body(ApiResponse.success("Logged out successfully.", null));
    }

    // ── GET /api/v1/auth/validate ─────────────────────────────────────────
    // Called by API Gateway / other microservices to verify a JWT
    @Operation(
        summary     = "Validate token",
        description = "Called by API Gateway to verify a JWT. Returns user info on success.")
    @GetMapping("/validate")
    public ResponseEntity<ApiResponse<UserInfoResponse>> validate(
            @RequestHeader("Authorization") String authHeader) {

        UserInfoResponse userInfo = authService.validateToken(authHeader);
        return ResponseEntity.ok(ApiResponse.success("Token is valid", userInfo));
    }

    // ── GET /api/v1/auth/me ───────────────────────────────────────────────
    // ✅ KEPT — different semantic purpose to /validate
    // /validate  = machine-to-machine (API Gateway calls this)
    // /me        = Angular calls this on page refresh to restore user state
    @Operation(
        summary     = "Get current user",
        description = "Decodes the bearer token and returns the user profile. "
                    + "Angular uses this to rebuild state after a page refresh.")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserInfoResponse>> me(
            @RequestHeader("Authorization") String authHeader) {

        UserInfoResponse userInfo = authService.validateToken(authHeader);
        return ResponseEntity.ok(ApiResponse.success("User info retrieved", userInfo));
    }

    // ── GET /api/v1/auth/health ───────────────────────────────────────────
    @Operation(summary = "Health check", description = "Liveness probe — no auth required")
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status",  "UP",
                "service", "auth-service"
        ));
    }
    // ── POST /api/v1/auth/change-password ─────────────────────────────────
    @Operation(
        summary     = "Change own password",
        description = "The signed-in user replaces their password. Ends all other sessions.")
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {

        authService.changePassword(authentication.getName(), request);
        // Every refresh token of this user was revoked, so drop the now-useless cookie too.
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshCookie.clear())
            .body(ApiResponse.success("Password changed. Please sign in again.", null));
    }
}
