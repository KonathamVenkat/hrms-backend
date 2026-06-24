package com.hrms.attendance.scheduler;

import com.hrms.attendance.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Nightly Attendance Summary Scheduler
 *
 * Runs at 23:55 every day to aggregate all attendance logs
 * for that day into ATTENDANCE_SUMMARY (upsert).
 *
 * The current day's data is ALWAYS served live from ATTENDANCE_LOGS
 * (hybrid approach) — this job only stores completed days.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AttendanceSummaryScheduler {

    private final AttendanceService attendanceService;

    /**
     * Cron: 55 23 * * * = 23:55 every night
     * Aggregates yesterday's logs in case of timezone drift.
     * Also aggregates today since it runs at end of business day.
     */
    @Scheduled(cron = "0 55 23 * * *")
    public void runNightlySummary() {
        LocalDate today = LocalDate.now();
        log.info("=== Nightly Attendance Summary START — {} ===", today);
        try {
            attendanceService.calculateAndStoreDailySummary(today);
            log.info("=== Nightly Attendance Summary COMPLETE — {} ===", today);
        } catch (Exception e) {
            log.error("Nightly summary FAILED for {}: {}", today, e.getMessage(), e);
        }
    }

    /**
     * Recovery job: runs at 00:05 to catch any missed previous-day entries
     * (handles cases where employees are in different timezones or late punches).
     */
    @Scheduled(cron = "0 5 0 * * *")
    public void runRecoveryForYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("=== Recovery Summary for {} ===", yesterday);
        try {
            attendanceService.calculateAndStoreDailySummary(yesterday);
        } catch (Exception e) {
            log.error("Recovery summary FAILED for {}: {}", yesterday, e.getMessage(), e);
        }
    }
}
