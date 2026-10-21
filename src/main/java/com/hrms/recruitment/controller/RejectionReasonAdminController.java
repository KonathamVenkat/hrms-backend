package com.hrms.recruitment.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.recruitment.dto.request.RejectionReasonRequest;
import com.hrms.recruitment.dto.response.RejectionReasonResponse;
import com.hrms.recruitment.service.RejectionReasonService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Rejection reason maintenance. SYSTEM_ADMIN only; dropdowns use GET /api/v1/recruitment/lookups/rejection-reasons. */
@RestController
@RequestMapping("/api/v1/admin/recruitment/rejection-reasons")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class RejectionReasonAdminController {

    private final RejectionReasonService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RejectionReasonResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("Rejection reasons fetched", service.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RejectionReasonResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Rejection reason fetched", service.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RejectionReasonResponse>> create(@Valid @RequestBody RejectionReasonRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created("Rejection reason created", service.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RejectionReasonResponse>> update(
            @PathVariable Long id, @Valid @RequestBody RejectionReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Rejection reason updated", service.update(id, request)));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).message("Rejection reason deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        service.activate(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).message("Rejection reason activated").statusCode(200).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).message("Rejection reason deleted").statusCode(200).build());
    }
}
