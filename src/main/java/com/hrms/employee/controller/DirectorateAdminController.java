package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.DirectorateRequest;
import com.hrms.employee.dto.response.DirectorateResponse;
import com.hrms.employee.service.DirectorateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Master-data maintenance for directorates. SYSTEM_ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/directorates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class DirectorateAdminController {

    private final DirectorateService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DirectorateResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<DirectorateResponse>>builder()
            .success(true).message("Directorate list fetched")
            .data(service.getAll()).statusCode(200).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DirectorateResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<DirectorateResponse>builder()
            .success(true).message("Directorate fetched")
            .data(service.getById(id)).statusCode(200).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DirectorateResponse>> create(@Valid @RequestBody DirectorateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<DirectorateResponse>builder()
                .success(true).message("Directorate created successfully")
                .data(service.create(request)).statusCode(201).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DirectorateResponse>> update(
            @PathVariable Long id, @Valid @RequestBody DirectorateRequest request) {
        return ResponseEntity.ok(ApiResponse.<DirectorateResponse>builder()
            .success(true).message("Directorate updated successfully")
            .data(service.update(id, request)).statusCode(200).build());
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Directorate deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Directorate activated").statusCode(200).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Directorate deleted").statusCode(200).build());
    }
}
