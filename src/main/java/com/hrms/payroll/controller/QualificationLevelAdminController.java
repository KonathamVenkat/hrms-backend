package com.hrms.payroll.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.payroll.dto.request.QualificationLevelRequest;
import com.hrms.payroll.dto.response.QualificationLevelResponse;
import com.hrms.payroll.service.QualificationLevelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Master-data maintenance for qualification levels, like the other masters. SYSTEM_ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/qualification-levels")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class QualificationLevelAdminController {

    private final QualificationLevelService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<QualificationLevelResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<QualificationLevelResponse>>builder()
            .success(true).message("Qualification level list fetched")
            .data(service.getAll()).statusCode(200).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<QualificationLevelResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<QualificationLevelResponse>builder()
            .success(true).message("Qualification level fetched")
            .data(service.getById(id)).statusCode(200).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<QualificationLevelResponse>> create(@Valid @RequestBody QualificationLevelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<QualificationLevelResponse>builder()
                .success(true).message("Qualification level created successfully")
                .data(service.create(request)).statusCode(201).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<QualificationLevelResponse>> update(
            @PathVariable Long id, @Valid @RequestBody QualificationLevelRequest request) {
        return ResponseEntity.ok(ApiResponse.<QualificationLevelResponse>builder()
            .success(true).message("Qualification level updated successfully")
            .data(service.update(id, request)).statusCode(200).build());
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Qualification level deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Qualification level activated").statusCode(200).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Qualification level deleted").statusCode(200).build());
    }
}
