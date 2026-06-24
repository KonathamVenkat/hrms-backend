package com.hrms.payroll.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.payroll.dto.request.SalaryComponentRequest;
import com.hrms.payroll.dto.response.SalaryComponentResponse;
import com.hrms.payroll.enums.ComponentType;
import com.hrms.payroll.service.SalaryComponentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payroll/components")
@RequiredArgsConstructor
@Slf4j
public class SalaryComponentController {

    private final SalaryComponentService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<SalaryComponentResponse>>> getAll(
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        log.info("GET /payroll/components — activeOnly={}", activeOnly);
        return ResponseEntity.ok(
                ApiResponse.success("Success", service.getAll(activeOnly)));
    }

    @GetMapping("/type/{type}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<SalaryComponentResponse>>> getByType(
            @PathVariable ComponentType type) {
        return ResponseEntity.ok(
                ApiResponse.success("Success", service.getByType(type)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<SalaryComponentResponse>> getById(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.success("Success", service.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<SalaryComponentResponse>> create(
            @Valid @RequestBody SalaryComponentRequest request) {
        log.info("POST /payroll/components — code={}", request.componentCode());
        return ResponseEntity.ok(
                ApiResponse.success("Salary component created successfully",
                        service.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<SalaryComponentResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody SalaryComponentRequest request) {
        log.info("PUT /payroll/components/{}", id);
        return ResponseEntity.ok(
                ApiResponse.success("Salary component updated successfully",
                        service.update(id, request)));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> toggleActive(
            @PathVariable Long id,
            @RequestParam boolean active) {
        service.toggleActive(id, active);
        return ResponseEntity.ok(
                ApiResponse.success(active ? "Component activated" : "Component deactivated",
                        null));
    }
}
