package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.ReligionRequest;
import com.hrms.employee.dto.response.ReligionResponse;
import com.hrms.employee.service.ReligionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Master-data maintenance for religions. SYSTEM_ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/religions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class ReligionAdminController {

    private final ReligionService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ReligionResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<ReligionResponse>>builder()
            .success(true).message("Religion list fetched")
            .data(service.getAll()).statusCode(200).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReligionResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<ReligionResponse>builder()
            .success(true).message("Religion fetched")
            .data(service.getById(id)).statusCode(200).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReligionResponse>> create(@Valid @RequestBody ReligionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<ReligionResponse>builder()
                .success(true).message("Religion created successfully")
                .data(service.create(request)).statusCode(201).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ReligionResponse>> update(
            @PathVariable Long id, @Valid @RequestBody ReligionRequest request) {
        return ResponseEntity.ok(ApiResponse.<ReligionResponse>builder()
            .success(true).message("Religion updated successfully")
            .data(service.update(id, request)).statusCode(200).build());
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Religion deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Religion activated").statusCode(200).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Religion deleted").statusCode(200).build());
    }
}
