package com.hrms.attendance.service;

import com.hrms.attendance.dto.request.CheckInRequest;
import com.hrms.attendance.dto.request.CheckOutRequest;
import com.hrms.attendance.dto.response.AttendanceLogResponse;
import com.hrms.attendance.dto.response.AttendanceSummaryResponse;
import com.hrms.attendance.dto.response.DayRecordsResult;
import com.hrms.common.dto.PagedResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceService {

    // ── Check-in / Check-out ──────────────────────────────────
    AttendanceLogResponse checkIn(CheckInRequest request);
    AttendanceLogResponse checkOut(CheckOutRequest request);

    // ── Read operations ───────────────────────────────────────
    AttendanceLogResponse getTodayLog(Long employeeId);

    AttendanceLogResponse getLogByDate(Long employeeId, LocalDate date);

    List<AttendanceLogResponse> getMonthlyLogs(Long employeeId, int year, int month);

    PagedResponse<AttendanceLogResponse> getAllLogs(
            Long employeeId, LocalDate from, LocalDate to, Pageable pageable);

    // ── Monthly summary (hybrid) ──────────────────────────────
    AttendanceSummaryResponse getMonthlySummary(Long employeeId, int year, int month);

    // ── Scheduler entry point ─────────────────────────────────
    void calculateAndStoreDailySummary(LocalDate date);

    /**
     * Writes or corrects the absent / weekend / holiday / leave rows for the finished days
     * {@code from..to} and refreshes the affected monthly summaries. Used by the nightly recovery
     * job (so a leave approved late fixes an earlier ABSENT row) and by HR for a back-fill.
     */
    DayRecordsResult regenerateDayRecords(LocalDate from, LocalDate to);
}
