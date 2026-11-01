package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.SectionRequest;
import com.hrms.employee.dto.response.SectionResponse;
import com.hrms.employee.service.SectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Master-data maintenance for sections. SYSTEM_ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/sections")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class SectionAdminController {

    private final SectionService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SectionResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<SectionResponse>>builder()
            .success(true).message("Section list fetched")
            .data(service.getAll()).statusCode(200).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SectionResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<SectionResponse>builder()
            .success(true).message("Section fetched")
            .data(service.getById(id)).statusCode(200).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SectionResponse>> create(@Valid @RequestBody SectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<SectionResponse>builder()
                .success(true).message("Section created successfully")
                .data(service.create(request)).statusCode(201).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SectionResponse>> update(
            @PathVariable Long id, @Valid @RequestBody SectionRequest request) {
        return ResponseEntity.ok(ApiResponse.<SectionResponse>builder()
            .success(true).message("Section updated successfully")
            .data(service.update(id, request)).statusCode(200).build());
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Section deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Section activated").statusCode(200).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Section deleted").statusCode(200).build());
    }
}
