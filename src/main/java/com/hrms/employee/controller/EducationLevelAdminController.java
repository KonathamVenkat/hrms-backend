package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.EducationLevelRequest;
import com.hrms.employee.dto.response.EducationLevelResponse;
import com.hrms.employee.service.EducationLevelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Master-data maintenance for education levels. SYSTEM_ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/education-levels")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class EducationLevelAdminController {

    private final EducationLevelService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<EducationLevelResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<EducationLevelResponse>>builder()
            .success(true).message("Education level list fetched")
            .data(service.getAll()).statusCode(200).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EducationLevelResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<EducationLevelResponse>builder()
            .success(true).message("Education level fetched")
            .data(service.getById(id)).statusCode(200).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EducationLevelResponse>> create(@Valid @RequestBody EducationLevelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<EducationLevelResponse>builder()
                .success(true).message("Education level created successfully")
                .data(service.create(request)).statusCode(201).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<EducationLevelResponse>> update(
            @PathVariable Long id, @Valid @RequestBody EducationLevelRequest request) {
        return ResponseEntity.ok(ApiResponse.<EducationLevelResponse>builder()
            .success(true).message("Education level updated successfully")
            .data(service.update(id, request)).statusCode(200).build());
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Education level deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Education level activated").statusCode(200).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Education level deleted").statusCode(200).build());
    }
}
