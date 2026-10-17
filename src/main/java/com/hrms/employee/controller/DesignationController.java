package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.response.DesignationLookupResponse;
import com.hrms.employee.service.DesignationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/designations")
@RequiredArgsConstructor
public class DesignationController {

    private final DesignationService designationService;

    /**
     * GET /api/v1/designations/lookup
     * Returns all active designations for dropdown/select lists.
     */
    @GetMapping("/lookup")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<DesignationLookupResponse>>> getAllDesignations() {
        return ResponseEntity.ok(
            ApiResponse.success("Designations fetched successfully", designationService.getActiveLookup())
        );
    }

    /**
     * GET /api/v1/designations/lookup/by-department/{departmentId}
     * Returns designations filtered by department — for cascading dropdown.
     */
    @GetMapping("/lookup/by-department/{departmentId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<DesignationLookupResponse>>> getByDepartment(
            @PathVariable Long departmentId) {
        return ResponseEntity.ok(
            ApiResponse.success("Designations fetched successfully",
                designationService.getActiveLookupByDepartment(departmentId))
        );
    }
}
