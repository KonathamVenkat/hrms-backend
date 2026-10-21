package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.LanguageRequest;
import com.hrms.employee.dto.response.LanguageResponse;
import com.hrms.employee.service.LanguageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Master-data maintenance for languages. SYSTEM_ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/languages")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class LanguageAdminController {

    private final LanguageService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<LanguageResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<LanguageResponse>>builder()
            .success(true).message("Language list fetched")
            .data(service.getAll()).statusCode(200).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LanguageResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<LanguageResponse>builder()
            .success(true).message("Language fetched")
            .data(service.getById(id)).statusCode(200).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LanguageResponse>> create(@Valid @RequestBody LanguageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<LanguageResponse>builder()
                .success(true).message("Language created successfully")
                .data(service.create(request)).statusCode(201).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<LanguageResponse>> update(
            @PathVariable Long id, @Valid @RequestBody LanguageRequest request) {
        return ResponseEntity.ok(ApiResponse.<LanguageResponse>builder()
            .success(true).message("Language updated successfully")
            .data(service.update(id, request)).statusCode(200).build());
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Language deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Language activated").statusCode(200).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Language deleted").statusCode(200).build());
    }
}
