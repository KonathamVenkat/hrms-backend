package com.hrms.attendance.service;

import com.hrms.attendance.dto.response.AttendanceSummaryResponse;
import com.hrms.common.dto.PagedResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service interface for Attendance Monthly Summary operations.
 *
 * Hybrid strategy:
 *  - Past months  → reads pre-aggregated ATTENDANCE_SUMMARY table (fast)
 *  - Current month → merges stored data with live today's log (real-time)
 */
public interface AttendanceSummaryService {

    /**
     * Get monthly summary for a single employee.
     * Hybrid: stored summary + live today if current month.
     */
    AttendanceSummaryResponse getEmployeeSummary(Long employeeId, int year, int month);

    /**
     * Get monthly summary for ALL employees — HR/Manager dashboard view.
     * Paginated. Returns stored summaries for past months.
     */
    PagedResponse<AttendanceSummaryResponse> getAllEmployeesSummary(
            int year, int month, Pageable pageable);

    /**
     * Get full year summary for one employee (12 months).
     * Used for yearly attendance trend chart.
     */
    List<AttendanceSummaryResponse> getYearlySummary(Long employeeId, int year);

    /**
     * Force recalculate summary for a specific employee + month.
     * Called by HR admin to correct stale data.
     */
    AttendanceSummaryResponse recalculateSummary(Long employeeId, int year, int month);
}
