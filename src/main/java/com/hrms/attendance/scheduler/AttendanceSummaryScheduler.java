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
     * Recovery job, 00:05: writes the absent / weekend / holiday / leave rows for yesterday, and
     * re-checks the previous week, so a leave approved or a holiday added a few days late corrects
     * the rows written earlier. Real punches and regularized days are never touched.
     */
    @Scheduled(cron = "0 5 0 * * *")
    public void runRecoveryForYesterday() {
        LocalDate today = LocalDate.now();
        LocalDate from  = today.minusDays(RECHECK_DAYS);
        LocalDate to    = today.minusDays(1);
        log.info("=== Day records and summaries {} .. {} ===", from, to);
        try {
            attendanceService.regenerateDayRecords(from, to);
        } catch (Exception e) {
            log.error("Day records FAILED for {} .. {}: {}", from, to, e.getMessage(), e);
        }
    }

    /** How many past days the recovery job re-checks. */
    static final int RECHECK_DAYS = 7;
}
