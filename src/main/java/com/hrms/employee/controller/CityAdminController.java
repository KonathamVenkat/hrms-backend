package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.CityRequest;
import com.hrms.employee.dto.response.CityResponse;
import com.hrms.employee.service.CityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Master-data maintenance for citys. SYSTEM_ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/cities")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class CityAdminController {

    private final CityService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CityResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<CityResponse>>builder()
            .success(true).message("City list fetched")
            .data(service.getAll()).statusCode(200).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CityResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<CityResponse>builder()
            .success(true).message("City fetched")
            .data(service.getById(id)).statusCode(200).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CityResponse>> create(@Valid @RequestBody CityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<CityResponse>builder()
                .success(true).message("City created successfully")
                .data(service.create(request)).statusCode(201).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CityResponse>> update(
            @PathVariable Long id, @Valid @RequestBody CityRequest request) {
        return ResponseEntity.ok(ApiResponse.<CityResponse>builder()
            .success(true).message("City updated successfully")
            .data(service.update(id, request)).statusCode(200).build());
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("City deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("City activated").statusCode(200).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("City deleted").statusCode(200).build());
    }
}
