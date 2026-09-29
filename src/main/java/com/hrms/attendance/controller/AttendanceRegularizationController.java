package com.hrms.attendance.controller;

import com.hrms.attendance.dto.request.RegularizationActionRequest;
import com.hrms.attendance.dto.request.RegularizationRequest;
import com.hrms.attendance.dto.response.RegularizationResponse;
import com.hrms.attendance.enums.RegularizationStatus;
import com.hrms.attendance.service.AttendanceRegularizationService;
import com.hrms.common.dto.ApiResponse;
import com.hrms.common.dto.PagedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/attendance/regularization")
@RequiredArgsConstructor
@Slf4j
public class AttendanceRegularizationController {

    private final AttendanceRegularizationService regService;

    // ── Employee: Submit new regularization ───────────────────
    @PostMapping("/submit")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    public ResponseEntity<ApiResponse<RegularizationResponse>> submit(
            @Valid @RequestBody RegularizationRequest request) {
        log.info("POST /regularization/submit — employeeId={}", request.employeeId());
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Regularization submitted successfully",
                        regService.submit(request)));
    }

    // ── Employee: Cancel own pending request ──────────────────
    @PatchMapping("/{regId}/cancel")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    public ResponseEntity<ApiResponse<RegularizationResponse>> cancel(
            @PathVariable Long regId,
            @RequestParam Long employeeId) {
        log.info("PATCH /regularization/{}/cancel — employeeId={}", regId, employeeId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Regularization cancelled successfully",
                        regService.cancel(regId, employeeId)));
    }

    // ── Employee: Get own requests ─────────────────────────────
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    public ResponseEntity<ApiResponse<PagedResponse<RegularizationResponse>>> getMyRequests(
            @RequestParam Long employeeId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("GET /regularization/my — employeeId={}", employeeId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Requests fetched successfully",
                        regService.getMyRequests(employeeId,
                                PageRequest.of(page, size,
                                        Sort.by(Sort.Direction.DESC, "createdAt")))));
    }

    // ── Get single regularization by ID ──────────────────────
    @GetMapping("/{regId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    public ResponseEntity<ApiResponse<RegularizationResponse>> getById(
            @PathVariable Long regId) {
        log.info("GET /regularization/{}", regId);
        return ResponseEntity.ok(
                ApiResponse.success("Success", regService.getById(regId)));
    }

    // ── HR/Manager: Approve ───────────────────────────────────
    @PatchMapping("/{regId}/approve")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<RegularizationResponse>> approve(
            @PathVariable Long regId,
            @Valid @RequestBody RegularizationActionRequest request) {
        log.info("PATCH /regularization/{}/approve", regId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Regularization approved successfully",
                        regService.approve(regId, request)));
    }

    // ── HR/Manager: Reject ────────────────────────────────────
    @PatchMapping("/{regId}/reject")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<RegularizationResponse>> reject(
            @PathVariable Long regId,
            @Valid @RequestBody RegularizationActionRequest request) {
        log.info("PATCH /regularization/{}/reject", regId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Regularization rejected",
                        regService.reject(regId, request)));
    }

    // ── HR/Manager: All requests with filters ─────────────────
    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<PagedResponse<RegularizationResponse>>> getAll(
            @RequestParam(required = false) RegularizationStatus status,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /regularization/all — status={}, employeeId={}", status, employeeId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "All requests fetched successfully",
                        regService.getAllRequests(status, employeeId, from, to,
                                PageRequest.of(page, size))));
    }

    // ── Badge count: pending requests ─────────────────────────
    @GetMapping("/pending-count")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<Long>> getPendingCount() {
        return ResponseEntity.ok(
                ApiResponse.success("Success", regService.getPendingCount()));
    }
}
