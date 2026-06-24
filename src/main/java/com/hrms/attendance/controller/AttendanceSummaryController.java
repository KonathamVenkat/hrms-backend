package com.hrms.attendance.controller;

import com.hrms.attendance.dto.response.AttendanceSummaryResponse;
import com.hrms.attendance.service.AttendanceSummaryService;
import com.hrms.common.dto.ApiResponse;
import com.hrms.common.dto.PagedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/attendance/summary")
@RequiredArgsConstructor
@Slf4j
public class AttendanceSummaryController {

    private final AttendanceSummaryService summaryService;

    // ── Single employee monthly summary ───────────────────────
    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    public ResponseEntity<ApiResponse<AttendanceSummaryResponse>> getEmployeeSummary(
            @PathVariable Long employeeId,
            @RequestParam int year,
            @RequestParam int month) {

        log.info("GET /summary/employee/{} — year={}, month={}", employeeId, year, month);
        AttendanceSummaryResponse response =
                summaryService.getEmployeeSummary(employeeId, year, month);
        return ResponseEntity.ok(ApiResponse.success("Summary fetched successfully", response));
    }

    // ── HR view: all employees monthly summary — paginated ────
    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<PagedResponse<AttendanceSummaryResponse>>> getAllSummaries(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("GET /summary/all — year={}, month={}, page={}, size={}",
                year, month, page, size);
        PagedResponse<AttendanceSummaryResponse> response =
                summaryService.getAllEmployeesSummary(year, month,
                        PageRequest.of(page, size, Sort.by("employeeCode")));
        return ResponseEntity.ok(ApiResponse.success("All summaries fetched successfully", response));
    }

    // ── Yearly trend (12 months) for one employee ─────────────
    @GetMapping("/employee/{employeeId}/yearly")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<AttendanceSummaryResponse>>> getYearlySummary(
            @PathVariable Long employeeId,
            @RequestParam int year) {

        log.info("GET /summary/employee/{}/yearly — year={}", employeeId, year);
        List<AttendanceSummaryResponse> response =
                summaryService.getYearlySummary(employeeId, year);
        return ResponseEntity.ok(ApiResponse.success("Yearly summary fetched successfully", response));
    }

    // ── Force recalculate — HR Admin corrective action ────────
    @PostMapping("/employee/{employeeId}/recalculate")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<AttendanceSummaryResponse>> recalculate(
            @PathVariable Long employeeId,
            @RequestParam int year,
            @RequestParam int month) {

        log.warn("POST /summary/employee/{}/recalculate — year={}, month={}",
                employeeId, year, month);
        AttendanceSummaryResponse response =
                summaryService.recalculateSummary(employeeId, year, month);
        return ResponseEntity.ok(ApiResponse.success("Summary recalculated successfully", response));
    }
}
