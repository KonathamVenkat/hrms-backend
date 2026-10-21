package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.RelationRequest;
import com.hrms.employee.dto.response.RelationResponse;
import com.hrms.employee.service.RelationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Master-data maintenance for relations. SYSTEM_ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/relations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class RelationAdminController {

    private final RelationService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RelationResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<RelationResponse>>builder()
            .success(true).message("Relation list fetched")
            .data(service.getAll()).statusCode(200).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RelationResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<RelationResponse>builder()
            .success(true).message("Relation fetched")
            .data(service.getById(id)).statusCode(200).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RelationResponse>> create(@Valid @RequestBody RelationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<RelationResponse>builder()
                .success(true).message("Relation created successfully")
                .data(service.create(request)).statusCode(201).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RelationResponse>> update(
            @PathVariable Long id, @Valid @RequestBody RelationRequest request) {
        return ResponseEntity.ok(ApiResponse.<RelationResponse>builder()
            .success(true).message("Relation updated successfully")
            .data(service.update(id, request)).statusCode(200).build());
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Relation deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Relation activated").statusCode(200).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Relation deleted").statusCode(200).build());
    }
}
