package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.DepartmentRequest;
import com.hrms.employee.dto.response.DepartmentResponse;
import com.hrms.employee.service.DepartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Master-data maintenance for departments. SYSTEM_ADMIN only; dropdowns use GET /api/v1/departments/lookup. */
@RestController
@RequestMapping("/api/v1/admin/departments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class DepartmentAdminController {

    private final DepartmentService departmentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DepartmentResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.<List<DepartmentResponse>>builder()
            .success(true).message("Departments fetched")
            .data(departmentService.getAllDepartments()).statusCode(200).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DepartmentResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<DepartmentResponse>builder()
            .success(true).message("Department fetched")
            .data(departmentService.getDepartmentById(id)).statusCode(200).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DepartmentResponse>> create(@Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<DepartmentResponse>builder()
                .success(true).message("Department created successfully")
                .data(departmentService.createDepartment(request)).statusCode(201).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DepartmentResponse>> update(
            @PathVariable Long id, @Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.ok(ApiResponse.<DepartmentResponse>builder()
            .success(true).message("Department updated successfully")
            .data(departmentService.updateDepartment(id, request)).statusCode(200).build());
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        departmentService.deactivateDepartment(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Department deactivated").statusCode(200).build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        departmentService.activateDepartment(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Department activated").statusCode(200).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        departmentService.deleteDepartment(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
            .success(true).message("Department deleted").statusCode(200).build());
    }
}
