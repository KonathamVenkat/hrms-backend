package com.hrms.auth.controller;

import com.hrms.auth.dto.request.*;
import com.hrms.auth.dto.response.*;
import com.hrms.auth.service.AuthService;
import com.hrms.common.dto.ApiResponse;          // ← add this import
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "JWT Authentication and Authorisation endpoints")
public class AuthController {

    private final AuthService authService;

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
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    // ── POST /api/v1/auth/refresh ─────────────────────────────────────────
    @Operation(
        summary     = "Refresh access token",
        description = "Exchanges a valid refresh token for a new access token.")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(  // ✅ wrapped
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {

        log.debug("Token refresh request received");
        LoginResponse response = authService.refreshToken(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", response));
    }

    // ── POST /api/v1/auth/logout ──────────────────────────────────────────
    @Operation(
        summary     = "Logout",
        description = "Revokes the refresh token server-side. "
                    + "Angular frontend must clear localStorage on receiving 200.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody LogoutRequest request) {  // ✅ @Valid enforced — no longer optional

        authService.logout(request);
        return ResponseEntity.ok(
                ApiResponse.success("Logged out successfully. Please clear your token.", null));
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
    
    @GetMapping("/generate-password")
    public String generatePassword(@RequestParam String password) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
        return encoder.encode(password);
    }
}