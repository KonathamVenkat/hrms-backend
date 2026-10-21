package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.WilayaRequest;
import com.hrms.employee.dto.response.WilayaResponse;
import com.hrms.employee.service.WilayaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Master-data maintenance for wilayas. SYSTEM_ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/wilayas")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class WilayaAdminController {

    private final WilayaService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<WilayaResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<WilayaResponse>>builder()
            .success(true).message("Wilaya list fetched")
            .data(service.getAll()).statusCode(200).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WilayaResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<WilayaResponse>builder()
            .success(true).message("Wilaya fetched")
            .data(service.getById(id)).statusCode(200).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WilayaResponse>> create(@Valid @RequestBody WilayaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<WilayaResponse>builder()
                .success(true).message("Wilaya created successfully")
                .data(service.create(request)).statusCode(201).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<WilayaResponse>> update(
            @PathVariable Long id, @Valid @RequestBody WilayaRequest request) {
        return ResponseEntity.ok(ApiResponse.<WilayaResponse>builder()
            .success(true).message("Wilaya updated successfully")
            .data(service.update(id, request)).statusCode(200).build());
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Wilaya deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Wilaya activated").statusCode(200).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Wilaya deleted").statusCode(200).build());
    }
}
