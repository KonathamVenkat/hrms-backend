package com.hrms.attendance.controller;

import com.hrms.attendance.dto.request.OvertimeActionRequest;
import com.hrms.attendance.dto.request.OvertimeSubmitRequest;
import com.hrms.attendance.dto.response.OvertimeResponse;
import com.hrms.attendance.enums.RegularizationStatus;
import com.hrms.attendance.service.OvertimeRequestService;
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
@RequestMapping("/api/v1/attendance/overtime")
@RequiredArgsConstructor
@Slf4j
public class OvertimeRequestController {

    private final OvertimeRequestService otService;

    // ── Employee: Submit new OT request ───────────────────
    @PostMapping("/submit")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    public ResponseEntity<ApiResponse<OvertimeResponse>> submit(
            @Valid @RequestBody OvertimeSubmitRequest request) {
        log.info("POST /overtime/submit — employeeId={}", request.employeeId());
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Overtime request submitted successfully",
                        otService.submit(request)));
    }

    // ── Employee: Cancel own pending request ──────────────
    @PatchMapping("/{otId}/cancel")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    public ResponseEntity<ApiResponse<OvertimeResponse>> cancel(
            @PathVariable String otId,
            @RequestParam Long employeeId) {
        log.info("PATCH /overtime/{}/cancel — employeeId={}", otId, employeeId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Overtime request cancelled successfully",
                        otService.cancel(otId, employeeId)));
    }

    // ── Employee: Get own requests ─────────────────────────
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    public ResponseEntity<ApiResponse<PagedResponse<OvertimeResponse>>> getMyRequests(
            @RequestParam Long employeeId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("GET /overtime/my — employeeId={}", employeeId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Overtime requests fetched successfully",
                        otService.getMyRequests(employeeId,
                                PageRequest.of(page, size,
                                        Sort.by(Sort.Direction.DESC, "createdAt")))));
    }

    // ── Get single OT request by ID ───────────────────────
    @GetMapping("/{otId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    public ResponseEntity<ApiResponse<OvertimeResponse>> getById(
            @PathVariable String otId) {
        log.info("GET /overtime/{}", otId);
        return ResponseEntity.ok(
                ApiResponse.success("Success", otService.getById(otId)));
    }

    // ── HR/Manager: Approve ───────────────────────────────
    @PatchMapping("/{otId}/approve")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<OvertimeResponse>> approve(
            @PathVariable String otId,
            @Valid @RequestBody OvertimeActionRequest request) {
        log.info("PATCH /overtime/{}/approve — reviewedBy={}", otId, request.reviewedBy());
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Overtime request approved successfully",
                        otService.approve(otId, request)));
    }

    // ── HR/Manager: Reject ────────────────────────────────
    @PatchMapping("/{otId}/reject")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<OvertimeResponse>> reject(
            @PathVariable String otId,
            @Valid @RequestBody OvertimeActionRequest request) {
        log.info("PATCH /overtime/{}/reject — reviewedBy={}", otId, request.reviewedBy());
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Overtime request rejected",
                        otService.reject(otId, request)));
    }

    // ── HR/Manager: All requests with filters ─────────────
    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<PagedResponse<OvertimeResponse>>> getAll(
            @RequestParam(required = false) RegularizationStatus status,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /overtime/all — status={}, employeeId={}", status, employeeId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "All overtime requests fetched successfully",
                        otService.getAllRequests(status, employeeId, from, to,
                                PageRequest.of(page, size))));
    }

    // ── Pending count for badge ────────────────────────────
    @GetMapping("/pending-count")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<Long>> getPendingCount() {
        return ResponseEntity.ok(
                ApiResponse.success("Success", otService.getPendingCount()));
    }
}
