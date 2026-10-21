package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.response.DepartmentLookupResponse;
import com.hrms.employee.dto.response.ManagerLookupResponse;
import com.hrms.employee.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    /**
     * GET /api/v1/departments/lookup
     * Returns all active departments for dropdown/select lists.
     * Used by Angular employee create/edit form.
     */
    @GetMapping("/lookup")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<DepartmentLookupResponse>>> getAllDepartments() {
        return ResponseEntity.ok(
            ApiResponse.success("Departments fetched successfully", departmentService.getActiveLookup())
        );
    }

    /**
     * GET /api/v1/departments/{departmentId}/managers
     * Manager/executive-grade employees currently in the department, for the reporting and
     * functional manager pickers on the job-details form.
     */
    @GetMapping("/{departmentId}/managers")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<ManagerLookupResponse>>> getManagers(@PathVariable Long departmentId) {
        return ResponseEntity.ok(
            ApiResponse.success("Managers fetched successfully", departmentService.getManagerCandidates(departmentId))
        );
    }
}
