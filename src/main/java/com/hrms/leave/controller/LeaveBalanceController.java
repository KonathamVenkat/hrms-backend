package com.hrms.leave.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.leave.dto.request.AdjustBalanceRequest;
import com.hrms.leave.dto.request.InitializeBalancesRequest;
import com.hrms.leave.dto.response.InitializationResultResponse;
import com.hrms.leave.dto.response.LeaveBalanceResponse;
import com.hrms.leave.service.LeaveBalanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class LeaveBalanceController {

    private final LeaveBalanceService leaveBalanceService;

    // ══════════════════════════════════════════════════════════
    // EMPLOYEE-FACING endpoints
    // GET /api/v1/employees/{employeeId}/leave-balances
    // ══════════════════════════════════════════════════════════

    /**
     * GET /api/v1/employees/{employeeId}/leave-balances?year=2026
     * Returns all leave balances for an employee.
     * Employee can view their own; HR can view anyone's.
     */
    @GetMapping("/api/v1/employees/{employeeId}/leave-balances")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<LeaveBalanceResponse>>> getEmployeeBalances(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Integer year) {

        log.info("GET leave balances — employeeId={} year={}", employeeId, year);
        return ResponseEntity.ok(
            ApiResponse.<List<LeaveBalanceResponse>>builder()
                .success(true)
                .message("Leave balances fetched successfully")
                .data(leaveBalanceService.getEmployeeBalances(employeeId, year))
                .statusCode(200)
                .build());
    }

    /**
     * POST /api/v1/employees/{employeeId}/leave-balances/initialize
     * Initialize balances for a single new employee.
     * Called automatically when a new employee is created.
     */
    @PostMapping("/api/v1/employees/{employeeId}/leave-balances/initialize")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<List<LeaveBalanceResponse>>> initializeForEmployee(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Integer year) {

        log.info("POST initialize balances — employeeId={} year={}", employeeId, year);
        return ResponseEntity.ok(
            ApiResponse.<List<LeaveBalanceResponse>>builder()
                .success(true)
                .message("Leave balances initialized successfully")
                .data(leaveBalanceService.initializeForEmployee(employeeId, year))
                .statusCode(200)
                .build());
    }

    /**
     * PATCH /api/v1/employees/{employeeId}/leave-balances/adjust
     * Manually adjust a single employee's leave balance.
     */
    @PatchMapping("/api/v1/employees/{employeeId}/leave-balances/adjust")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<LeaveBalanceResponse>> adjustBalance(
            @PathVariable Long employeeId,
            @Valid @RequestBody AdjustBalanceRequest request) {

        log.info("PATCH adjust balance — employeeId={} type={} type={}",
            employeeId, request.getLeaveTypeCode(), request.getAdjustmentType());
        return ResponseEntity.ok(
            ApiResponse.<LeaveBalanceResponse>builder()
                .success(true)
                .message("Leave balance adjusted successfully")
                .data(leaveBalanceService.adjustBalance(employeeId, request))
                .statusCode(200)
                .build());
    }

    // ══════════════════════════════════════════════════════════
    // ADMIN endpoints
    // /api/v1/admin/leave-balances
    // ══════════════════════════════════════════════════════════

    /**
     * GET /api/v1/admin/leave-balances?year=2026
     * All employees' balances for a year — HR admin dashboard.
     */
    @GetMapping("/api/v1/admin/leave-balances")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<List<LeaveBalanceResponse>>> getAllBalancesForYear(
            @RequestParam(required = false) Integer year) {

        log.info("GET all leave balances for year={}", year);
        return ResponseEntity.ok(
            ApiResponse.<List<LeaveBalanceResponse>>builder()
                .success(true)
                .message("All leave balances fetched")
                .data(leaveBalanceService.getAllBalancesForYear(year))
                .statusCode(200)
                .build());
    }

    /**
     * POST /api/v1/admin/leave-balances/initialize
     * Bulk initialize balances for all (or selected) employees.
     * Primary admin action at start of each year.
     */
    @PostMapping("/api/v1/admin/leave-balances/initialize")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<InitializationResultResponse>> initializeBalances(
            @Valid @RequestBody InitializeBalancesRequest request) {

        log.info("POST bulk initialize balances — year={} employees={}",
            request.getYear(),
            request.getEmployeeIds() == null ? "ALL" : request.getEmployeeIds().size());

        return ResponseEntity.ok(
            ApiResponse.<InitializationResultResponse>builder()
                .success(true)
                .message("Leave balance initialization completed")
                .data(leaveBalanceService.initializeBalances(request))
                .statusCode(200)
                .build());
    }
}
