package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.ProvinceRequest;
import com.hrms.employee.dto.response.ProvinceResponse;
import com.hrms.employee.service.ProvinceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Master-data maintenance for provinces. SYSTEM_ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/provinces")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class ProvinceAdminController {

    private final ProvinceService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProvinceResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<ProvinceResponse>>builder()
            .success(true).message("Province list fetched")
            .data(service.getAll()).statusCode(200).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProvinceResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<ProvinceResponse>builder()
            .success(true).message("Province fetched")
            .data(service.getById(id)).statusCode(200).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProvinceResponse>> create(@Valid @RequestBody ProvinceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<ProvinceResponse>builder()
                .success(true).message("Province created successfully")
                .data(service.create(request)).statusCode(201).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProvinceResponse>> update(
            @PathVariable Long id, @Valid @RequestBody ProvinceRequest request) {
        return ResponseEntity.ok(ApiResponse.<ProvinceResponse>builder()
            .success(true).message("Province updated successfully")
            .data(service.update(id, request)).statusCode(200).build());
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Province deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Province activated").statusCode(200).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Province deleted").statusCode(200).build());
    }
}
