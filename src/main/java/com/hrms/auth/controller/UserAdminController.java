package com.hrms.auth.controller;

import com.hrms.auth.dto.request.ResetPasswordRequest;
import com.hrms.auth.service.AuthService;
import com.hrms.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final AuthService authService;

    /**
     * POST /api/v1/admin/users/reset-password
     * HR_ADMIN sets a temporary password for an employee's account (e.g. after a lock-out).
     * The user is unlocked, must change it at next sign-in, and all their sessions end.
     */
    @PostMapping("/reset-password")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            Authentication authentication) {

        authService.resetPassword(request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(
                "Temporary password set. The user must change it at next sign-in.", null));
    }
}
