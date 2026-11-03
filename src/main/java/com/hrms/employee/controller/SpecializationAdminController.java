package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.SpecializationRequest;
import com.hrms.employee.dto.response.SpecializationResponse;
import com.hrms.employee.service.SpecializationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Master-data maintenance for specializations. SYSTEM_ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/specializations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class SpecializationAdminController {

    private final SpecializationService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SpecializationResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<SpecializationResponse>>builder()
            .success(true).message("Specialization list fetched")
            .data(service.getAll()).statusCode(200).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SpecializationResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<SpecializationResponse>builder()
            .success(true).message("Specialization fetched")
            .data(service.getById(id)).statusCode(200).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SpecializationResponse>> create(@Valid @RequestBody SpecializationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<SpecializationResponse>builder()
                .success(true).message("Specialization created successfully")
                .data(service.create(request)).statusCode(201).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SpecializationResponse>> update(
            @PathVariable Long id, @Valid @RequestBody SpecializationRequest request) {
        return ResponseEntity.ok(ApiResponse.<SpecializationResponse>builder()
            .success(true).message("Specialization updated successfully")
            .data(service.update(id, request)).statusCode(200).build());
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Specialization deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Specialization activated").statusCode(200).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Specialization deleted").statusCode(200).build());
    }
}
