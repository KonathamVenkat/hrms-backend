package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.RelationTypeRequest;
import com.hrms.employee.dto.response.RelationTypeResponse;
import com.hrms.employee.service.RelationTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Master-data maintenance for relation types. SYSTEM_ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/relation-types")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class RelationTypeAdminController {

    private final RelationTypeService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RelationTypeResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<RelationTypeResponse>>builder()
            .success(true).message("Relation type list fetched")
            .data(service.getAll()).statusCode(200).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RelationTypeResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<RelationTypeResponse>builder()
            .success(true).message("Relation type fetched")
            .data(service.getById(id)).statusCode(200).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RelationTypeResponse>> create(@Valid @RequestBody RelationTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<RelationTypeResponse>builder()
                .success(true).message("Relation type created successfully")
                .data(service.create(request)).statusCode(201).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RelationTypeResponse>> update(
            @PathVariable Long id, @Valid @RequestBody RelationTypeRequest request) {
        return ResponseEntity.ok(ApiResponse.<RelationTypeResponse>builder()
            .success(true).message("Relation type updated successfully")
            .data(service.update(id, request)).statusCode(200).build());
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Relation type deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Relation type activated").statusCode(200).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Relation type deleted").statusCode(200).build());
    }
}
