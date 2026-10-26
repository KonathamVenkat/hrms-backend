package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.DepartmentSecretaryRequest;
import com.hrms.employee.dto.response.DepartmentSecretaryResponse;
import com.hrms.employee.service.DepartmentSecretaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Who is secretary of an employee's current department; HR sets it from the employee's job details. */
@RestController
@RequestMapping("/api/v1/employees/{employeeId}/department-secretary")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
public class DepartmentSecretaryController {

    private final DepartmentSecretaryService departmentSecretaryService;

    @GetMapping
    public ResponseEntity<ApiResponse<DepartmentSecretaryResponse>> get(@PathVariable Long employeeId) {
        return ResponseEntity.ok(ApiResponse.<DepartmentSecretaryResponse>builder()
            .success(true).message("Department secretary fetched")
            .data(departmentSecretaryService.get(employeeId)).statusCode(200).build());
    }

    @PutMapping
    public ResponseEntity<ApiResponse<DepartmentSecretaryResponse>> set(
            @PathVariable Long employeeId, @Valid @RequestBody DepartmentSecretaryRequest request) {
        return ResponseEntity.ok(ApiResponse.<DepartmentSecretaryResponse>builder()
            .success(true).message("Department secretary updated")
            .data(departmentSecretaryService.set(employeeId, request.secretary())).statusCode(200).build());
    }
}
