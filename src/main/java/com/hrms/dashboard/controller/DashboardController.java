package com.hrms.dashboard.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.dashboard.dto.response.HrDashboardResponse;
import com.hrms.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * GET /api/v1/dashboard/hr-summary
     * Workforce headline numbers + pending approval counts. HR only — an employee's own
     * dashboard is composed from their own (already access-guarded) endpoints.
     */
    @GetMapping("/hr-summary")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<HrDashboardResponse>> getHrSummary() {
        return ResponseEntity.ok(
                ApiResponse.success("Success", dashboardService.getHrSummary()));
    }
}
