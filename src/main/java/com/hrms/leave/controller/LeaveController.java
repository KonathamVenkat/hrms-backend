package com.hrms.leave.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.common.dto.PagedResponse;
import com.hrms.leave.dto.request.ApproveLeaveRequest;
import com.hrms.leave.dto.request.CreateLeaveRequest;
import com.hrms.leave.dto.request.LeaveFilterRequest;
import com.hrms.leave.dto.response.LeaveAttachmentDownload;
import com.hrms.leave.dto.response.LeaveBalanceResponse;
import com.hrms.leave.dto.response.LeaveRequestResponse;
import com.hrms.leave.service.LeaveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    // ══════════════════════════════════════════════════════════
    // EMPLOYEE — Apply / Cancel / View own leaves
    // ══════════════════════════════════════════════════════════

    /**
     * POST /api/v1/employees/{employeeId}/leave-requests
     */
    @PostMapping(value = "/employees/{employeeId}/leave-requests",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> applyLeave(
            @PathVariable Long employeeId,
            @Valid @RequestPart("request") CreateLeaveRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file) {

        log.info("POST apply leave — emp={} type={} {} to {}",
            employeeId, request.getLeaveTypeCode(),
            request.getStartDate(), request.getEndDate());

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.<LeaveRequestResponse>builder()
                .success(true)
                .message("Leave request submitted successfully")
                .data(leaveService.applyLeave(employeeId, request, file))
                .statusCode(201)
                .build());
    }

    /**
     * GET /api/v1/employees/{employeeId}/leave-requests
     */
    @GetMapping("/employees/{employeeId}/leave-requests")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    public ResponseEntity<ApiResponse<PagedResponse<LeaveRequestResponse>>> getMyLeaves(
            @PathVariable Long employeeId,
            @RequestParam(required = false) String  status,
            @RequestParam(required = false) String  leaveTypeCode,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "0")         Integer page,
            @RequestParam(defaultValue = "10")        Integer size,
            @RequestParam(defaultValue = "createdAt") String  sortBy,
            @RequestParam(defaultValue = "desc")      String  sortDir) {

        LeaveFilterRequest filter = LeaveFilterRequest.builder()
            .status(status).leaveTypeCode(leaveTypeCode).year(year)
            .page(page).size(size).sortBy(sortBy).sortDir(sortDir)
            .build();

        return ResponseEntity.ok(
            ApiResponse.<PagedResponse<LeaveRequestResponse>>builder()
                .success(true)
                .message("Leave requests fetched successfully")
                .data(leaveService.getLeavesByEmployee(employeeId, filter))
                .statusCode(200)
                .build());
    }

    /**
     * GET /api/v1/employees/{employeeId}/leave-requests/{leaveReqId}
     */
    @GetMapping("/employees/{employeeId}/leave-requests/{leaveReqId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> getLeaveById(
            @PathVariable Long employeeId,
            @PathVariable Long leaveReqId) {

        return ResponseEntity.ok(
            ApiResponse.<LeaveRequestResponse>builder()
                .success(true)
                .message("Leave request fetched")
                .data(leaveService.getLeaveById(employeeId, leaveReqId))
                .statusCode(200)
                .build());
    }

    /**
     * GET /api/v1/employees/{employeeId}/leave-requests/{leaveReqId}/attachment
     * Owner or HR only (enforced in the service via EmployeeAccessGuard).
     */
    @GetMapping("/employees/{employeeId}/leave-requests/{leaveReqId}/attachment")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable Long employeeId,
            @PathVariable Long leaveReqId) {

        LeaveAttachmentDownload download = leaveService.getAttachment(employeeId, leaveReqId);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                    .filename(download.fileName(), StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(download.resource());
    }

    /**
     * PATCH /api/v1/employees/{employeeId}/leave-requests/{leaveReqId}/cancel
     */
    @PatchMapping("/employees/{employeeId}/leave-requests/{leaveReqId}/cancel")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    public ResponseEntity<ApiResponse<Void>> cancelLeave(
            @PathVariable Long employeeId,
            @PathVariable Long leaveReqId) {

        log.info("PATCH cancel — emp={} leaveReqId={}", employeeId, leaveReqId);
        leaveService.cancelLeave(leaveReqId, employeeId);

        return ResponseEntity.ok(
            ApiResponse.<Void>builder()
                .success(true)
                .message("Leave request cancelled successfully")
                .statusCode(200)
                .build());
    }

    /**
     * GET /api/v1/employees/{employeeId}/leave-balances/summary
     */
    @GetMapping("/employees/{employeeId}/leave-balances/summary")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<LeaveBalanceResponse>>> getBalanceSummary(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Integer year) {

        return ResponseEntity.ok(
            ApiResponse.<List<LeaveBalanceResponse>>builder()
                .success(true)
                .message("Leave balance summary fetched")
                .data(leaveService.getBalances(employeeId, year))
                .statusCode(200)
                .build());
    }

    // ══════════════════════════════════════════════════════════
    // HR / MANAGER — View all requests + Approve / Reject
    // ══════════════════════════════════════════════════════════

    /**
     * GET /api/v1/leave-requests
     * HR/Manager views all leave requests (paginated + filtered).
     * Supports: status, leaveTypeCode, employeeId filters.
     */
    @GetMapping("/leave-requests")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<PagedResponse<LeaveRequestResponse>>> getAllLeaves(
            @RequestParam(required = false) String  status,
            @RequestParam(required = false) String  leaveTypeCode,
            @RequestParam(required = false) Long    employeeId,
            @RequestParam(defaultValue = "0")         Integer page,
            @RequestParam(defaultValue = "10")        Integer size,
            @RequestParam(defaultValue = "createdAt") String  sortBy,
            @RequestParam(defaultValue = "desc")      String  sortDir) {

        LeaveFilterRequest filter = LeaveFilterRequest.builder()
            .status(status).leaveTypeCode(leaveTypeCode)
            .employeeId(employeeId)
            .page(page).size(size).sortBy(sortBy).sortDir(sortDir)
            .build();

        return ResponseEntity.ok(
            ApiResponse.<PagedResponse<LeaveRequestResponse>>builder()
                .success(true)
                .message("Leave requests fetched")
                .data(leaveService.getAllLeaves(filter))
                .statusCode(200)
                .build());
    }

    /**
     * PATCH /api/v1/leave-requests/{leaveReqId}/process
     * HR/Manager approves or rejects a leave request.
     */
    @PatchMapping("/leave-requests/{leaveReqId}/process")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> processLeave(
            @PathVariable Long leaveReqId,
            @Valid @RequestBody ApproveLeaveRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("PATCH process — leaveReqId={} action={}",
            leaveReqId, request.getAction());

        String approver = userDetails != null
            ? userDetails.getUsername() : "SYSTEM";

        return ResponseEntity.ok(
            ApiResponse.<LeaveRequestResponse>builder()
                .success(true)
                .message("Leave request "
                    + request.getAction().toLowerCase()
                    + " successfully")
                .data(leaveService.processLeave(leaveReqId, request, approver))
                .statusCode(200)
                .build());
    }

    /**
     * GET /api/v1/leave-requests/pending-count
     * Quick count of pending requests for badge/notification.
     */
    @GetMapping("/leave-requests/pending-count")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<Long>> getPendingCount() {
        return ResponseEntity.ok(
            ApiResponse.<Long>builder()
                .success(true)
                .message("Pending count fetched")
                .data(leaveService.getPendingCount())
                .statusCode(200)
                .build());
    }
}
