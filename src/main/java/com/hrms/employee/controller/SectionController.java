package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.response.SectionLookupResponse;
import com.hrms.employee.service.SectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sections")
@RequiredArgsConstructor
public class SectionController {

    private final SectionService sectionService;

    /** GET /api/v1/sections/lookup/by-department/{departmentId}: active sections of a department, for the cascading dropdown. */
    @GetMapping("/lookup/by-department/{departmentId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<SectionLookupResponse>>> getByDepartment(@PathVariable Long departmentId) {
        return ResponseEntity.ok(
            ApiResponse.success("Sections fetched successfully", sectionService.getActiveLookupByDepartment(departmentId)));
    }
}
