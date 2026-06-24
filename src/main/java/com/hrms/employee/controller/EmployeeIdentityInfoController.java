package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.IdentityInfoRequest;
import com.hrms.employee.dto.response.IdentityInfoResponse;
import com.hrms.employee.service.IdentityInfoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/employees/{employeeId}/identity")
@RequiredArgsConstructor
public class EmployeeIdentityInfoController {

    private final IdentityInfoService identityService;

    /**
     * GET /api/v1/employees/{employeeId}/identity
     * Returns identity info — empty shell if not yet created.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<IdentityInfoResponse>> getIdentityInfo(
            @PathVariable Long employeeId) {

        log.info("GET identity info — employeeId: {}", employeeId);
        return ResponseEntity.ok(
            ApiResponse.<IdentityInfoResponse>builder()
                .success(true)
                .message("Identity info fetched successfully")
                .data(identityService.getIdentityInfo(employeeId))
                .statusCode(200)
                .build()
        );
    }

    /**
     * PUT /api/v1/employees/{employeeId}/identity
     * Create or update identity info (upsert).
     * Single endpoint handles both create and update.
     */
    @PutMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<IdentityInfoResponse>> saveIdentityInfo(
            @PathVariable Long employeeId,
            @Valid @RequestBody IdentityInfoRequest request) {

        log.info("PUT identity info — employeeId: {}", employeeId);
        return ResponseEntity.ok(
            ApiResponse.<IdentityInfoResponse>builder()
                .success(true)
                .message("Identity info saved successfully")
                .data(identityService.saveIdentityInfo(employeeId, request))
                .statusCode(200)
                .build()
        );
    }
}
