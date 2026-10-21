package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.GradeRequest;
import com.hrms.employee.dto.response.GradeResponse;
import com.hrms.employee.service.GradeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Master-data maintenance for grades. SYSTEM_ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/grades")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class GradeAdminController {

    private final GradeService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<GradeResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<GradeResponse>>builder()
            .success(true).message("Grade list fetched")
            .data(service.getAll()).statusCode(200).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GradeResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<GradeResponse>builder()
            .success(true).message("Grade fetched")
            .data(service.getById(id)).statusCode(200).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GradeResponse>> create(@Valid @RequestBody GradeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<GradeResponse>builder()
                .success(true).message("Grade created successfully")
                .data(service.create(request)).statusCode(201).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<GradeResponse>> update(
            @PathVariable Long id, @Valid @RequestBody GradeRequest request) {
        return ResponseEntity.ok(ApiResponse.<GradeResponse>builder()
            .success(true).message("Grade updated successfully")
            .data(service.update(id, request)).statusCode(200).build());
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Grade deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Grade activated").statusCode(200).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Grade deleted").statusCode(200).build());
    }
}
