package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.LanguageLevelRequest;
import com.hrms.employee.dto.response.LanguageLevelResponse;
import com.hrms.employee.service.LanguageLevelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Master-data maintenance for language levels. SYSTEM_ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/language-levels")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class LanguageLevelAdminController {

    private final LanguageLevelService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<LanguageLevelResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<LanguageLevelResponse>>builder()
            .success(true).message("Language level list fetched")
            .data(service.getAll()).statusCode(200).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LanguageLevelResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<LanguageLevelResponse>builder()
            .success(true).message("Language level fetched")
            .data(service.getById(id)).statusCode(200).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LanguageLevelResponse>> create(@Valid @RequestBody LanguageLevelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<LanguageLevelResponse>builder()
                .success(true).message("Language level created successfully")
                .data(service.create(request)).statusCode(201).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<LanguageLevelResponse>> update(
            @PathVariable Long id, @Valid @RequestBody LanguageLevelRequest request) {
        return ResponseEntity.ok(ApiResponse.<LanguageLevelResponse>builder()
            .success(true).message("Language level updated successfully")
            .data(service.update(id, request)).statusCode(200).build());
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Language level deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Language level activated").statusCode(200).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Language level deleted").statusCode(200).build());
    }
}
