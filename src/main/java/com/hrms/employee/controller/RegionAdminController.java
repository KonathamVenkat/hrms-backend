package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.RegionRequest;
import com.hrms.employee.dto.response.RegionResponse;
import com.hrms.employee.service.RegionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Master-data maintenance for regions. SYSTEM_ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/regions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class RegionAdminController {

    private final RegionService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RegionResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<RegionResponse>>builder()
            .success(true).message("Region list fetched")
            .data(service.getAll()).statusCode(200).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RegionResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<RegionResponse>builder()
            .success(true).message("Region fetched")
            .data(service.getById(id)).statusCode(200).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RegionResponse>> create(@Valid @RequestBody RegionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<RegionResponse>builder()
                .success(true).message("Region created successfully")
                .data(service.create(request)).statusCode(201).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RegionResponse>> update(
            @PathVariable Long id, @Valid @RequestBody RegionRequest request) {
        return ResponseEntity.ok(ApiResponse.<RegionResponse>builder()
            .success(true).message("Region updated successfully")
            .data(service.update(id, request)).statusCode(200).build());
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Region deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Region activated").statusCode(200).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Region deleted").statusCode(200).build());
    }
}
