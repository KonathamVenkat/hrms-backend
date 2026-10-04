package com.hrms.attendance.controller;

import com.hrms.attendance.dto.request.CheckInRequest;
import com.hrms.attendance.dto.request.CheckOutRequest;
import com.hrms.attendance.dto.response.AttendanceLogResponse;
import com.hrms.attendance.dto.response.DayRecordsResult;
import com.hrms.attendance.dto.response.AttendanceSummaryResponse;
import com.hrms.attendance.service.AttendanceService;
import com.hrms.common.dto.ApiResponse;
import com.hrms.common.dto.PagedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    // ── Check-In ──────────────────────────────────────────────
    @PostMapping("/check-in")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    public ResponseEntity<ApiResponse<AttendanceLogResponse>> checkIn(
            @Valid @RequestBody CheckInRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Checked in successfully",
                        attendanceService.checkIn(request)));
    }

    // ── Check-Out ─────────────────────────────────────────────
    @PostMapping("/check-out")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    public ResponseEntity<ApiResponse<AttendanceLogResponse>> checkOut(
            @Valid @RequestBody CheckOutRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Checked out successfully",
                        attendanceService.checkOut(request)));
    }

    // ── Today's log for a specific employee ───────────────────
    @GetMapping("/today/{employeeId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    public ResponseEntity<ApiResponse<AttendanceLogResponse>> getTodayLog(
            @PathVariable Long employeeId) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Success",
                        attendanceService.getTodayLog(employeeId)));
    }

    // ── Single log by date ────────────────────────────────────
    @GetMapping("/{employeeId}/date/{date}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    public ResponseEntity<ApiResponse<AttendanceLogResponse>> getLogByDate(
            @PathVariable Long employeeId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Success",
                        attendanceService.getLogByDate(employeeId, date)));
    }

    // ── Monthly logs list for an employee ─────────────────────
    @GetMapping("/{employeeId}/monthly")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<AttendanceLogResponse>>> getMonthlyLogs(
            @PathVariable Long employeeId,
            @RequestParam int year,
            @RequestParam int month) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Success",
                        attendanceService.getMonthlyLogs(employeeId, year, month)));
    }

    // ── Paginated list — HR dashboard ─────────────────────────
    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
    public ResponseEntity<ApiResponse<PagedResponse<AttendanceLogResponse>>> getAllLogs(
            @RequestParam(required = false) Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Success",
                        attendanceService.getAllLogs(employeeId, from, to,
                                PageRequest.of(page, size))));
    }

    // ── Monthly summary (hybrid) ──────────────────────────────
    @GetMapping("/{employeeId}/summary")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    public ResponseEntity<ApiResponse<AttendanceSummaryResponse>> getMonthlySummary(
            @PathVariable Long employeeId,
            @RequestParam int year,
            @RequestParam int month) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Success",
                        attendanceService.getMonthlySummary(employeeId, year, month)));
    }

    // ── Back-fill / repair the absent, weekend, holiday and leave rows ──
    @PostMapping("/admin/day-records")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<DayRecordsResult>> regenerateDayRecords(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Day records generated",
                        attendanceService.regenerateDayRecords(from, to)));
    }
}
