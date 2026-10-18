package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.CompanyBranchRequest;
import com.hrms.employee.dto.response.CompanyBranchResponse;
import com.hrms.employee.service.CompanyBranchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Master-data maintenance for company branchs. SYSTEM_ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/company-branches")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class CompanyBranchAdminController {

    private final CompanyBranchService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CompanyBranchResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<CompanyBranchResponse>>builder()
            .success(true).message("Company branch list fetched")
            .data(service.getAll()).statusCode(200).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CompanyBranchResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<CompanyBranchResponse>builder()
            .success(true).message("Company branch fetched")
            .data(service.getById(id)).statusCode(200).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CompanyBranchResponse>> create(@Valid @RequestBody CompanyBranchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<CompanyBranchResponse>builder()
                .success(true).message("Company branch created successfully")
                .data(service.create(request)).statusCode(201).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CompanyBranchResponse>> update(
            @PathVariable Long id, @Valid @RequestBody CompanyBranchRequest request) {
        return ResponseEntity.ok(ApiResponse.<CompanyBranchResponse>builder()
            .success(true).message("Company branch updated successfully")
            .data(service.update(id, request)).statusCode(200).build());
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Company branch deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Company branch activated").statusCode(200).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Company branch deleted").statusCode(200).build());
    }
}
