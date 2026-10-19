package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.DepartmentHeadRequest;
import com.hrms.employee.dto.response.DepartmentHeadResponse;
import com.hrms.employee.service.DepartmentHeadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Who heads an employee's current department; HR sets it from the employee's job details. */
@RestController
@RequestMapping("/api/v1/employees/{employeeId}/department-head")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
public class DepartmentHeadController {

    private final DepartmentHeadService departmentHeadService;

    @GetMapping
    public ResponseEntity<ApiResponse<DepartmentHeadResponse>> get(@PathVariable Long employeeId) {
        return ResponseEntity.ok(ApiResponse.<DepartmentHeadResponse>builder()
            .success(true).message("Department head fetched")
            .data(departmentHeadService.get(employeeId)).statusCode(200).build());
    }

    @PutMapping
    public ResponseEntity<ApiResponse<DepartmentHeadResponse>> set(
            @PathVariable Long employeeId, @Valid @RequestBody DepartmentHeadRequest request) {
        return ResponseEntity.ok(ApiResponse.<DepartmentHeadResponse>builder()
            .success(true).message("Department head updated")
            .data(departmentHeadService.set(employeeId, request.head())).statusCode(200).build());
    }
}
