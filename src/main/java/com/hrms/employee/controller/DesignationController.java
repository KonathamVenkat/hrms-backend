package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.response.DesignationLookupResponse;
import com.hrms.employee.entity.Designation;
import com.hrms.employee.repository.DesignationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;
 
@RestController
@RequestMapping("/api/v1/designations")
@RequiredArgsConstructor
public class DesignationController {
 
    private final DesignationRepository designationRepository;
 
    /**
     * GET /api/v1/designations/lookup
     * Returns all active designations for dropdown/select lists.
     */
    @GetMapping("/lookup")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<DesignationLookupResponse>>> getAllDesignations() {
 
        List<DesignationLookupResponse> list = designationRepository
            .findByIsActiveOrderByTitleAsc(1)
            .stream()
            .map(d -> DesignationLookupResponse.builder()
                .id(d.getDesigId())
                .code(d.getCode())
                .title(d.getTitle())
                .titleAr(d.getTitleAr())
                .gradeLevel(d.getGradeLevel())
                .departmentId(d.getDepartmentId())
                .build())
            .collect(Collectors.toList());
 
        return ResponseEntity.ok(
            ApiResponse.success("Designations fetched successfully", list)
        );
    }
 
    /**
     * GET /api/v1/designations/lookup?departmentId={id}
     * Returns designations filtered by department — for cascading dropdown.
     */
    @GetMapping("/lookup/by-department/{departmentId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<DesignationLookupResponse>>> getByDepartment(
            @PathVariable Long departmentId) {
 
        List<DesignationLookupResponse> list = designationRepository
            .findByDepartmentIdAndIsActiveOrderByTitleAsc(departmentId, 1)
            .stream()
            .map(d -> DesignationLookupResponse.builder()
                .id(d.getDesigId())
                .code(d.getCode())
                .title(d.getTitle())
                .titleAr(d.getTitleAr())
                .gradeLevel(d.getGradeLevel())
                .departmentId(d.getDepartmentId())
                .build())
            .collect(Collectors.toList());
 
        return ResponseEntity.ok(
            ApiResponse.success("Designations fetched successfully", list)
        );
    }
}