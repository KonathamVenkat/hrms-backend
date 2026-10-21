package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.ProfessionRequest;
import com.hrms.employee.dto.response.ProfessionResponse;
import com.hrms.employee.service.ProfessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Master-data maintenance for professions. SYSTEM_ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/professions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class ProfessionAdminController {

    private final ProfessionService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProfessionResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<ProfessionResponse>>builder()
            .success(true).message("Profession list fetched")
            .data(service.getAll()).statusCode(200).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProfessionResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<ProfessionResponse>builder()
            .success(true).message("Profession fetched")
            .data(service.getById(id)).statusCode(200).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProfessionResponse>> create(@Valid @RequestBody ProfessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<ProfessionResponse>builder()
                .success(true).message("Profession created successfully")
                .data(service.create(request)).statusCode(201).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProfessionResponse>> update(
            @PathVariable Long id, @Valid @RequestBody ProfessionRequest request) {
        return ResponseEntity.ok(ApiResponse.<ProfessionResponse>builder()
            .success(true).message("Profession updated successfully")
            .data(service.update(id, request)).statusCode(200).build());
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Profession deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Profession activated").statusCode(200).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Profession deleted").statusCode(200).build());
    }
}
