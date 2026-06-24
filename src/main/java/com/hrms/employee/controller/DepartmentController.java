package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.response.DepartmentLookupResponse;
import com.hrms.employee.entity.Department;
import com.hrms.employee.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;
 
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {
 
    private final DepartmentRepository departmentRepository;
 
    /**
     * GET /api/v1/departments/lookup
     * Returns all active departments for dropdown/select lists.
     * Used by Angular employee create/edit form.
     */
    @GetMapping("/lookup")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<DepartmentLookupResponse>>> getAllDepartments() {
 
        List<DepartmentLookupResponse> departments = departmentRepository
            .findByIsActiveOrderByNameAsc(1)
            .stream()
            .map(d -> DepartmentLookupResponse.builder()
                .id(d.getDeptId())
                .code(d.getCode())
                .name(d.getName())
                .nameAr(d.getNameAr())
                .costCenterCode(d.getCostCenterCode())
                .build())
            .collect(Collectors.toList());
 
        return ResponseEntity.ok(
            ApiResponse.success("Departments fetched successfully", departments)
        );
    }
}