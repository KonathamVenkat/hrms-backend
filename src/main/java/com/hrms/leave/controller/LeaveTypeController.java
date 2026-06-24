package com.hrms.leave.controller;



import com.hrms.common.dto.ApiResponse;
import com.hrms.leave.dto.request.LeaveTypeRequest;
import com.hrms.leave.dto.response.LeaveTypeResponse;
import com.hrms.leave.service.LeaveTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/leave-types")
@RequiredArgsConstructor
public class LeaveTypeController {

    private final LeaveTypeService leaveTypeService;

    /**
     * GET /api/v1/admin/leave-types
     * Returns ALL leave types (active + inactive) for admin table.
     */
    @GetMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<List<LeaveTypeResponse>>> getAllLeaveTypes() {
        log.info("GET /api/v1/admin/leave-types");
        return ResponseEntity.ok(
            ApiResponse.<List<LeaveTypeResponse>>builder()
                .success(true)
                .message("Leave types fetched successfully")
                .data(leaveTypeService.getAllLeaveTypes())
                .statusCode(200)
                .build()
        );
    }

    /**
     * GET /api/v1/admin/leave-types/active
     * Returns only active leave types — used by leave request dropdowns.
     */
    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<LeaveTypeResponse>>> getActiveLeaveTypes() {
        return ResponseEntity.ok(
            ApiResponse.<List<LeaveTypeResponse>>builder()
                .success(true)
                .message("Active leave types fetched")
                .data(leaveTypeService.getActiveLeaveTypes())
                .statusCode(200)
                .build()
        );
    }

    /**
     * GET /api/v1/admin/leave-types/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<LeaveTypeResponse>> getLeaveTypeById(
            @PathVariable Long id) {
        return ResponseEntity.ok(
            ApiResponse.<LeaveTypeResponse>builder()
                .success(true)
                .message("Leave type fetched")
                .data(leaveTypeService.getLeaveTypeById(id))
                .statusCode(200)
                .build()
        );
    }

    /**
     * POST /api/v1/admin/leave-types
     */
    @PostMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<LeaveTypeResponse>> createLeaveType(
            @Valid @RequestBody LeaveTypeRequest request) {
        log.info("POST /api/v1/admin/leave-types - code: {}", request.getCode());
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                ApiResponse.<LeaveTypeResponse>builder()
                    .success(true)
                    .message("Leave type created successfully")
                    .data(leaveTypeService.createLeaveType(request))
                    .statusCode(201)
                    .build()
            );
    }

    /**
     * PUT /api/v1/admin/leave-types/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<LeaveTypeResponse>> updateLeaveType(
            @PathVariable Long id,
            @Valid @RequestBody LeaveTypeRequest request) {
        log.info("PUT /api/v1/admin/leave-types/{}", id);
        return ResponseEntity.ok(
            ApiResponse.<LeaveTypeResponse>builder()
                .success(true)
                .message("Leave type updated successfully")
                .data(leaveTypeService.updateLeaveType(id, request))
                .statusCode(200)
                .build()
        );
    }

    /**
     * PATCH /api/v1/admin/leave-types/{id}/deactivate
     */
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateLeaveType(@PathVariable Long id) {
        log.info("PATCH /api/v1/admin/leave-types/{}/deactivate", id);
        leaveTypeService.deactivateLeaveType(id);
        return ResponseEntity.ok(
            ApiResponse.<Void>builder()
                .success(true)
                .message("Leave type deactivated")
                .statusCode(200)
                .build()
        );
    }

    /**
     * PATCH /api/v1/admin/leave-types/{id}/activate
     */
    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> activateLeaveType(@PathVariable Long id) {
        log.info("PATCH /api/v1/admin/leave-types/{}/activate", id);
        leaveTypeService.activateLeaveType(id);
        return ResponseEntity.ok(
            ApiResponse.<Void>builder()
                .success(true)
                .message("Leave type activated")
                .statusCode(200)
                .build()
        );
    }
}