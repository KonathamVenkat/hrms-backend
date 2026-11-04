package com.hrms.employee.controller;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.response.EmployeeCompletenessResponse;
import com.hrms.employee.service.EmployeeCompletenessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Which profile tabs of one employee already have their required records. HR staff, or the employee themself. */
@RestController
@RequestMapping("/api/v1/employees/{employeeId}/completeness")
@RequiredArgsConstructor
public class EmployeeCompletenessController {

    private final EmployeeCompletenessService service;
    private final EmployeeAccessGuard accessGuard;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<EmployeeCompletenessResponse>> getCompleteness(@PathVariable Long employeeId) {
        accessGuard.assertSelfOrPrivileged(employeeId);
        return ResponseEntity.ok(ApiResponse.<EmployeeCompletenessResponse>builder()
            .success(true).message("Completeness fetched successfully")
            .data(service.getCompleteness(employeeId)).statusCode(200).build());
    }
}
