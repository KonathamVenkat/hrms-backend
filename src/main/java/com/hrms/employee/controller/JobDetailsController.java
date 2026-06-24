package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.request.JobDetailsRequest;
import com.hrms.employee.dto.response.JobDetailsResponse;
import com.hrms.employee.service.JobDetailsService;
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
@RequestMapping("/api/v1/employees/{employeeId}/job-details")
@RequiredArgsConstructor
public class JobDetailsController {

    private final JobDetailsService jobDetailsService;

    /**
     * GET /api/v1/employees/{employeeId}/job-details/current
     * Returns the active job assignment for this employee.
     */
    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<JobDetailsResponse>> getCurrentJob(
            @PathVariable Long employeeId) {

        log.info("GET current job — employeeId: {}", employeeId);
        return ResponseEntity.ok(
            ApiResponse.<JobDetailsResponse>builder()
                .success(true)
                .message("Current job fetched successfully")
                .data(jobDetailsService.getCurrentJob(employeeId))
                .statusCode(200)
                .build()
        );
    }

    /**
     * GET /api/v1/employees/{employeeId}/job-details/history
     * Returns full SCD Type 2 job history — newest first.
     */
    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<JobDetailsResponse>>> getJobHistory(
            @PathVariable Long employeeId) {

        log.info("GET job history — employeeId: {}", employeeId);
        return ResponseEntity.ok(
            ApiResponse.<List<JobDetailsResponse>>builder()
                .success(true)
                .message("Job history fetched successfully")
                .data(jobDetailsService.getJobHistory(employeeId))
                .statusCode(200)
                .build()
        );
    }

    /**
     * POST /api/v1/employees/{employeeId}/job-details
     * Assigns a new job (creates SCD Type 2 record, closes previous).
     * Used when employee is: transferred, promoted, or location changed.
     */
    @PostMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<JobDetailsResponse>> assignJob(
            @PathVariable Long employeeId,
            @Valid @RequestBody JobDetailsRequest request) {

        log.info("POST assign job — employeeId: {}", employeeId);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                ApiResponse.<JobDetailsResponse>builder()
                    .success(true)
                    .message("Job assigned successfully")
                    .data(jobDetailsService.assignJob(employeeId, request))
                    .statusCode(201)
                    .build()
            );
    }

    /**
     * PUT /api/v1/employees/{employeeId}/job-details/current
     * Updates non-structural fields of current job (shift, work mode, remarks).
     * Does NOT create a new history entry.
     */
    @PutMapping("/current")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<JobDetailsResponse>> updateCurrentJob(
            @PathVariable Long employeeId,
            @Valid @RequestBody JobDetailsRequest request) {

        log.info("PUT update current job — employeeId: {}", employeeId);
        return ResponseEntity.ok(
            ApiResponse.<JobDetailsResponse>builder()
                .success(true)
                .message("Job details updated successfully")
                .data(jobDetailsService.updateCurrentJob(employeeId, request))
                .statusCode(200)
                .build()
        );
    }
}

