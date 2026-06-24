package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.WorkShiftRequest;
import com.hrms.employee.dto.response.WorkShiftResponse;
import com.hrms.employee.service.WorkShiftService;
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
@RequestMapping("/api/v1/admin/work-shifts")
@RequiredArgsConstructor
public class WorkShiftController {

    private final WorkShiftService workShiftService;

    /**
     * GET /api/v1/admin/work-shifts
     * All shifts — admin list view.
     */
    @GetMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<List<WorkShiftResponse>>> getAllShifts() {
        log.info("GET /api/v1/admin/work-shifts");
        return ResponseEntity.ok(
            ApiResponse.<List<WorkShiftResponse>>builder()
                .success(true)
                .message("Work shifts fetched successfully")
                .data(workShiftService.getAllShifts())
                .statusCode(200)
                .build()
        );
    }

    /**
     * GET /api/v1/admin/work-shifts/active
     * Active shifts only — for employee job details dropdown.
     */
    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<WorkShiftResponse>>> getActiveShifts() {
        return ResponseEntity.ok(
            ApiResponse.<List<WorkShiftResponse>>builder()
                .success(true)
                .message("Active work shifts fetched")
                .data(workShiftService.getActiveShifts())
                .statusCode(200)
                .build()
        );
    }

    /**
     * GET /api/v1/admin/work-shifts/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<WorkShiftResponse>> getShiftById(
            @PathVariable Long id) {
        return ResponseEntity.ok(
            ApiResponse.<WorkShiftResponse>builder()
                .success(true)
                .message("Work shift fetched")
                .data(workShiftService.getShiftById(id))
                .statusCode(200)
                .build()
        );
    }

    /**
     * POST /api/v1/admin/work-shifts
     */
    @PostMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<WorkShiftResponse>> createShift(
            @Valid @RequestBody WorkShiftRequest request) {
        log.info("POST /api/v1/admin/work-shifts - {}", request.getShiftCode());
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                ApiResponse.<WorkShiftResponse>builder()
                    .success(true)
                    .message("Work shift created successfully")
                    .data(workShiftService.createShift(request))
                    .statusCode(201)
                    .build()
            );
    }

    /**
     * PUT /api/v1/admin/work-shifts/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<WorkShiftResponse>> updateShift(
            @PathVariable Long id,
            @Valid @RequestBody WorkShiftRequest request) {
        log.info("PUT /api/v1/admin/work-shifts/{}", id);
        return ResponseEntity.ok(
            ApiResponse.<WorkShiftResponse>builder()
                .success(true)
                .message("Work shift updated successfully")
                .data(workShiftService.updateShift(id, request))
                .statusCode(200)
                .build()
        );
    }

    /**
     * PATCH /api/v1/admin/work-shifts/{id}/deactivate
     */
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateShift(@PathVariable Long id) {
        workShiftService.deactivateShift(id);
        return ResponseEntity.ok(
            ApiResponse.<Void>builder()
                .success(true).message("Work shift deactivated").statusCode(200).build()
        );
    }

    /**
     * PATCH /api/v1/admin/work-shifts/{id}/activate
     */
    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> activateShift(@PathVariable Long id) {
        workShiftService.activateShift(id);
        return ResponseEntity.ok(
            ApiResponse.<Void>builder()
                .success(true).message("Work shift activated").statusCode(200).build()
        );
    }
}
