package com.hrms.recruitment.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.recruitment.dto.request.StageRequest;
import com.hrms.recruitment.dto.response.StageResponse;
import com.hrms.recruitment.service.StageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Pipeline stage maintenance. SYSTEM_ADMIN only; dropdowns use GET /api/v1/recruitment/lookups/stages. */
@RestController
@RequestMapping("/api/v1/admin/recruitment/stages")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class StageAdminController {

    private final StageService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StageResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("Stages fetched", service.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StageResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Stage fetched", service.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StageResponse>> create(@Valid @RequestBody StageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created("Stage created", service.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<StageResponse>> update(@PathVariable Long id, @Valid @RequestBody StageRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Stage updated", service.update(id, request)));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).message("Stage deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).message("Stage activated").statusCode(200).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).message("Stage deleted").statusCode(200).build());
    }
}
