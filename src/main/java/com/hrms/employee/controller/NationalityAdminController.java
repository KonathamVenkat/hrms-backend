package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.NationalityRequest;
import com.hrms.employee.dto.response.NationalityResponse;
import com.hrms.employee.service.NationalityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Master-data maintenance for nationalitys. SYSTEM_ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/nationalities")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class NationalityAdminController {

    private final NationalityService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NationalityResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<NationalityResponse>>builder()
            .success(true).message("Nationality list fetched")
            .data(service.getAll()).statusCode(200).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NationalityResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<NationalityResponse>builder()
            .success(true).message("Nationality fetched")
            .data(service.getById(id)).statusCode(200).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NationalityResponse>> create(@Valid @RequestBody NationalityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<NationalityResponse>builder()
                .success(true).message("Nationality created successfully")
                .data(service.create(request)).statusCode(201).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<NationalityResponse>> update(
            @PathVariable Long id, @Valid @RequestBody NationalityRequest request) {
        return ResponseEntity.ok(ApiResponse.<NationalityResponse>builder()
            .success(true).message("Nationality updated successfully")
            .data(service.update(id, request)).statusCode(200).build());
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Nationality deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Nationality activated").statusCode(200).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Nationality deleted").statusCode(200).build());
    }
}
