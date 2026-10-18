package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.CompanyRequest;
import com.hrms.employee.dto.response.CompanyResponse;
import com.hrms.employee.service.CompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Master-data maintenance for companys. SYSTEM_ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/companies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class CompanyAdminController {

    private final CompanyService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CompanyResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<CompanyResponse>>builder()
            .success(true).message("Company list fetched")
            .data(service.getAll()).statusCode(200).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CompanyResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<CompanyResponse>builder()
            .success(true).message("Company fetched")
            .data(service.getById(id)).statusCode(200).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CompanyResponse>> create(@Valid @RequestBody CompanyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<CompanyResponse>builder()
                .success(true).message("Company created successfully")
                .data(service.create(request)).statusCode(201).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CompanyResponse>> update(
            @PathVariable Long id, @Valid @RequestBody CompanyRequest request) {
        return ResponseEntity.ok(ApiResponse.<CompanyResponse>builder()
            .success(true).message("Company updated successfully")
            .data(service.update(id, request)).statusCode(200).build());
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Company deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Company activated").statusCode(200).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Company deleted").statusCode(200).build());
    }
}
