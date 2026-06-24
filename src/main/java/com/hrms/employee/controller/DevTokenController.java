package com.hrms.employee.controller;

import com.hrms.common.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * DEVELOPMENT ONLY — generates test JWT tokens for Bruno/Postman testing.
 * This controller is ONLY active when profile is "dev" or "local".
 * It will NOT be available in production.
 *
 * Remove this class entirely when auth-service is implemented.
 */
@RestController
@RequestMapping("/dev/token")
@RequiredArgsConstructor
@Profile({"dev", "local", "default"})   // ✅ only active in dev profiles
public class DevTokenController {

    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/admin")
    public Map<String, String> getAdminToken() {
        String token = jwtTokenProvider.generateAccessToken(
            "admin-uuid-0001",              // userUuid  → becomes JWT "sub"
            "admin@company.com",            // email
            List.of("HR_ADMIN"),            // roles
            null                            // employeeId
        );
        return Map.of(
            "token", token,
            "bearer", "Bearer " + token,
            "role", "HR_ADMIN",
            "usage", "Paste the 'token' value into Bruno Bearer token field"
        );
    }

    @GetMapping("/manager")
    public Map<String, String> getManagerToken() {
        String token = jwtTokenProvider.generateAccessToken(
            "manager-uuid-0002",
            "manager@company.com",
            List.of("HR_MANAGER"),
            null
        );
        return Map.of(
            "token", token,
            "bearer", "Bearer " + token,
            "role", "HR_MANAGER"
        );
    }

    @GetMapping("/employee")
    public Map<String, String> getEmployeeToken() {
        String token = jwtTokenProvider.generateAccessToken(
            "employee-uuid-0003",
            "employee@company.com",
            List.of("EMPLOYEE"),
            1L                              // linked to employee ID 1
        );
        return Map.of(
            "token", token,
            "bearer", "Bearer " + token,
            "role", "EMPLOYEE"
        );
    }
}