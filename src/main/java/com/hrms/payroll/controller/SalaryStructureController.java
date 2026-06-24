package com.hrms.payroll.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.payroll.dto.request.SalaryStructureRequest;
import com.hrms.payroll.dto.response.SalaryStructureResponse;
import com.hrms.payroll.service.SalaryStructureService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payroll/structures")
@RequiredArgsConstructor
@Slf4j
public class SalaryStructureController {

    private final SalaryStructureService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<SalaryStructureResponse>>> getAll(
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        log.info("GET /payroll/structures");
        return ResponseEntity.ok(
                ApiResponse.success("Success", service.getAll(activeOnly)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<SalaryStructureResponse>> getById(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.success("Success", service.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<SalaryStructureResponse>> create(
            @Valid @RequestBody SalaryStructureRequest request) {
        log.info("POST /payroll/structures — code={}", request.structureCode());
        return ResponseEntity.ok(
                ApiResponse.success("Salary structure created successfully",
                        service.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<SalaryStructureResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody SalaryStructureRequest request) {
        log.info("PUT /payroll/structures/{}", id);
        return ResponseEntity.ok(
                ApiResponse.success("Salary structure updated successfully",
                        service.update(id, request)));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> toggleActive(
            @PathVariable Long id,
            @RequestParam boolean active) {
        service.toggleActive(id, active);
        return ResponseEntity.ok(
                ApiResponse.success(active ? "Structure activated" : "Structure deactivated",
                        null));
    }
}
