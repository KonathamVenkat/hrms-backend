package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.DesignationRequest;
import com.hrms.employee.dto.response.DesignationResponse;
import com.hrms.employee.service.DesignationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Master-data maintenance for designations. SYSTEM_ADMIN only; dropdowns use GET /api/v1/designations/lookup. */
@RestController
@RequestMapping("/api/v1/admin/designations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class DesignationAdminController {

    private final DesignationService designationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DesignationResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<DesignationResponse>>builder()
            .success(true).message("Designations fetched")
            .data(designationService.getAllDesignations()).statusCode(200).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DesignationResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<DesignationResponse>builder()
            .success(true).message("Designation fetched")
            .data(designationService.getDesignationById(id)).statusCode(200).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DesignationResponse>> create(@Valid @RequestBody DesignationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<DesignationResponse>builder()
                .success(true).message("Designation created successfully")
                .data(designationService.createDesignation(request)).statusCode(201).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DesignationResponse>> update(
            @PathVariable Long id, @Valid @RequestBody DesignationRequest request) {
        return ResponseEntity.ok(ApiResponse.<DesignationResponse>builder()
            .success(true).message("Designation updated successfully")
            .data(designationService.updateDesignation(id, request)).statusCode(200).build());
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        designationService.deactivateDesignation(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Designation deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        designationService.activateDesignation(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Designation activated").statusCode(200).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        designationService.deleteDesignation(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Designation deleted").statusCode(200).build());
    }
}
