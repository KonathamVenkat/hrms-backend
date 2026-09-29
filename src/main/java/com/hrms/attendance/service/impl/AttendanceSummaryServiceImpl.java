package com.hrms.attendance.service.impl;

import com.hrms.attendance.dto.response.AttendanceSummaryResponse;
import com.hrms.attendance.entity.AttendanceLog;
import com.hrms.attendance.entity.AttendanceSummary;
import com.hrms.attendance.repository.AttendanceLogRepository;
import com.hrms.attendance.repository.AttendanceSummaryRepository;
import com.hrms.attendance.service.AttendanceSummaryService;
import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.dto.PagedResponse;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceSummaryServiceImpl implements AttendanceSummaryService {

    private final AttendanceSummaryRepository summaryRepo;
    private final AttendanceLogRepository     logRepo;
    private final EmployeeRepository          employeeRepo;
    private final EmployeeAccessGuard         accessGuard;

    // ────────────────────────────────────────────────────────
    // SINGLE EMPLOYEE MONTHLY SUMMARY — HYBRID
    // ────────────────────────────────────────────────────────
    @Override
    public AttendanceSummaryResponse getEmployeeSummary(Long employeeId, int year, int month) {
        accessGuard.assertSelfOrPrivileged(employeeId);
        AttendanceCalculator.validateYearMonth(year, month);
        log.info("Fetching attendance summary — employeeId={}, year={}, month={}",
                employeeId, year, month);

        Employee employee  = findEmployee(employeeId);
        LocalDate now      = LocalDate.now();
        boolean isCurrent  = (now.getYear() == year && now.getMonthValue() == month);

        AttendanceSummary stored = summaryRepo
                .findByEmployeeIdAndSummaryYearAndSummaryMonth(employeeId, year, month)
                .orElseGet(() -> {
                    log.debug("No stored summary found for employeeId={} {}/{} — " +
                            "returning empty summary", employeeId, year, month);
                    return buildEmptySummary(employee, year, month);
                });

        if (!isCurrent) {
            log.debug("Past month requested — returning pre-aggregated summary for {}/{}",
                    year, month);
            return toResponse(stored, employee, false);
        }

        // Current month: merge stored (days 1 → yesterday) with live today
        log.debug("Current month — merging stored summary with live today's log");
        Optional<AttendanceLog> todayLog = logRepo
                .findByEmployeeIdAndAttendanceDateAndIsActive(employeeId, now, 1);

        AttendanceSummary merged = mergeTodayIntoSummary(stored, todayLog.orElse(null));
        log.info("Summary fetched successfully for employeeId={} — present={}, absent={}",
                employeeId, merged.getPresentDays(), merged.getAbsentDays());

        return toResponse(merged, employee, true);
    }

    // ────────────────────────────────────────────────────────
    // ALL EMPLOYEES MONTHLY SUMMARY — HR VIEW (PAGINATED)
    // ────────────────────────────────────────────────────────
    @Override
    public PagedResponse<AttendanceSummaryResponse> getAllEmployeesSummary(
            int year, int month, Pageable pageable) {

        log.info("Fetching all employees attendance summary — year={}, month={}, page={}",
                year, month, pageable.getPageNumber());

        Page<AttendanceSummary> page = summaryRepo
                .findBySummaryYearAndSummaryMonthOrderByEmployeeCodeAsc(year, month, pageable);

        log.info("Retrieved {} summary records for {}/{}", page.getTotalElements(), year, month);

        java.util.Map<Long, Employee> employees = employeeRepo
                .findAllById(page.getContent().stream()
                        .map(AttendanceSummary::getEmployeeId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Employee::getId, e -> e));

        return PagedResponse.from(page.map(summary -> {
            Employee emp = employees.get(summary.getEmployeeId());
            if (emp == null) throw new ResourceNotFoundException("Employee", "id", summary.getEmployeeId());
            return toResponse(summary, emp, false);
        }));
    }

    // ────────────────────────────────────────────────────────
    // YEARLY SUMMARY — 12 MONTHS TREND
    // ────────────────────────────────────────────────────────
    @Override
    public List<AttendanceSummaryResponse> getYearlySummary(Long employeeId, int year) {
        accessGuard.assertSelfOrPrivileged(employeeId);
        AttendanceCalculator.validateYearMonth(year, 1);
        log.info("Fetching yearly summary — employeeId={}, year={}", employeeId, year);

        Employee employee = findEmployee(employeeId);
        List<AttendanceSummary> summaries = summaryRepo
                .findByEmployeeIdAndSummaryYearOrderBySummaryMonthAsc(employeeId, year);

        log.info("Found {} monthly records for employee {} in year {}",
                summaries.size(), employeeId, year);

        LocalDate now = LocalDate.now();
        return summaries.stream()
                .map(s -> {
                    boolean isCurrent = (now.getYear() == year
                            && now.getMonthValue() == s.getSummaryMonth());
                    return toResponse(s, employee, isCurrent);
                })
                .collect(Collectors.toList());
    }

    // ────────────────────────────────────────────────────────
    // FORCE RECALCULATE — HR ADMIN CORRECTIVE ACTION
    // ────────────────────────────────────────────────────────
    @Override
    @Transactional
    public AttendanceSummaryResponse recalculateSummary(Long employeeId, int year, int month) {
        AttendanceCalculator.validateYearMonth(year, month);
        log.warn("Force recalculation triggered — employeeId={}, year={}, month={}",
                employeeId, year, month);

        Employee employee  = findEmployee(employeeId);
        LocalDate from     = LocalDate.of(year, month, 1);
        LocalDate monthEnd = from.withDayOfMonth(from.lengthOfMonth());

        // For current month — aggregate up to yesterday; today stays live
        LocalDate to = LocalDate.now().minusDays(1);
        if (to.isAfter(monthEnd)) to = monthEnd;

        log.debug("Recalculating from {} to {} for employeeId={}", from, to, employeeId);

        List<AttendanceLog> logs = logRepo
                .findByEmployeeIdAndAttendanceDateBetweenAndIsActiveOrderByAttendanceDateAsc(
                        employeeId, from, to, 1);

        log.info("Found {} attendance log entries to recalculate", logs.size());

        // Aggregate
        BigDecimal presentDays = BigDecimal.ZERO;
        BigDecimal absentDays  = BigDecimal.ZERO;
        BigDecimal halfDays    = BigDecimal.ZERO;
        BigDecimal leaveDays   = BigDecimal.ZERO;
        int lateDays = 0, holidayDays = 0, weekendDays = 0;
        long workMins = 0, otMins = 0, lateMins = 0;

        for (AttendanceLog l : logs) {
            switch (l.getStatus()) {
                case PRESENT  -> presentDays = presentDays.add(BigDecimal.ONE);
                case LATE     -> { presentDays = presentDays.add(BigDecimal.ONE); lateDays++; }
                case ABSENT   -> absentDays   = absentDays.add(BigDecimal.ONE);
                case HALF_DAY -> halfDays     = halfDays.add(new BigDecimal("0.5"));
                case ON_LEAVE -> leaveDays    = leaveDays.add(BigDecimal.ONE);
                case HOLIDAY  -> holidayDays++;
                case WEEKEND  -> weekendDays++;
            }
            workMins += l.getWorkingMinutes()  != null ? l.getWorkingMinutes()  : 0;
            otMins   += l.getOvertimeMinutes() != null ? l.getOvertimeMinutes() : 0;
            lateMins += l.getLateMinutes()     != null ? l.getLateMinutes()     : 0;
        }

        // Upsert summary
        AttendanceSummary summary = summaryRepo
                .findByEmployeeIdAndSummaryYearAndSummaryMonth(employeeId, year, month)
                .orElseGet(() -> {
                    Long newId = summaryRepo.findNextSequenceValue();
                    AttendanceSummary s = AttendanceSummary.builder()
                            .summaryId(newId)
                            .employeeId(employeeId)
                            .employeeCode(employee.getEmployeeCode())
                            .summaryYear(year)
                            .summaryMonth(month)
                            .build();
                    s.setCreatedAt(LocalDateTime.now());
                    s.setCreatedBy(employee.getEmployeeCode());
                    return s;
                });

        summary.setPresentDays(presentDays);
        summary.setAbsentDays(absentDays);
        summary.setHalfDays(halfDays);
        summary.setLateDays(lateDays);
        summary.setLeaveDays(leaveDays);
        summary.setHolidayDays(holidayDays);
        summary.setWeekendDays(weekendDays);
        summary.setTotalWorkingMins(workMins);
        summary.setTotalOvertimeMins(otMins);
        summary.setTotalLateMins(lateMins);
        summary.setLastCalculatedAt(LocalDateTime.now());
        summary.setUpdatedAt(LocalDateTime.now());
        summary.setUpdatedBy(employee.getEmployeeCode());

        AttendanceSummary saved = summaryRepo.save(summary);
        log.info("Recalculation complete — employeeId={}, present={}, absent={}",
                employeeId, saved.getPresentDays(), saved.getAbsentDays());

        return toResponse(saved, employee, false);
    }

    // ── Private helpers ───────────────────────────────────────

    private AttendanceSummary mergeTodayIntoSummary(
            AttendanceSummary base, AttendanceLog today) {

        if (today == null) {
            log.debug("No attendance log for today — returning stored summary as-is");
            return base;
        }

        log.debug("Merging today's log (status={}) into summary", today.getStatus());

        AttendanceSummary m = AttendanceSummary.builder()
                .summaryId(base.getSummaryId())
                .employeeId(base.getEmployeeId())
                .employeeCode(base.getEmployeeCode())
                .summaryYear(base.getSummaryYear())
                .summaryMonth(base.getSummaryMonth())
                .presentDays(base.getPresentDays())
                .absentDays(base.getAbsentDays())
                .halfDays(base.getHalfDays())
                .lateDays(base.getLateDays())
                .leaveDays(base.getLeaveDays())
                .holidayDays(base.getHolidayDays())
                .weekendDays(base.getWeekendDays())
                .totalWorkingMins(base.getTotalWorkingMins())
                .totalOvertimeMins(base.getTotalOvertimeMins())
                .totalLateMins(base.getTotalLateMins())
                .lastCalculatedAt(base.getLastCalculatedAt())
                .build();

        switch (today.getStatus()) {
            case PRESENT  -> m.setPresentDays(m.getPresentDays().add(BigDecimal.ONE));
            case LATE     -> { m.setPresentDays(m.getPresentDays().add(BigDecimal.ONE));
                               m.setLateDays(m.getLateDays() + 1); }
            case ABSENT   -> m.setAbsentDays(m.getAbsentDays().add(BigDecimal.ONE));
            case HALF_DAY -> m.setHalfDays(m.getHalfDays().add(new BigDecimal("0.5")));
            case ON_LEAVE -> m.setLeaveDays(m.getLeaveDays().add(BigDecimal.ONE));
            case HOLIDAY  -> m.setHolidayDays(m.getHolidayDays() + 1);
            case WEEKEND  -> m.setWeekendDays(m.getWeekendDays() + 1);
        }

        m.setTotalWorkingMins(m.getTotalWorkingMins()
                + (today.getWorkingMinutes()  != null ? today.getWorkingMinutes()  : 0));
        m.setTotalOvertimeMins(m.getTotalOvertimeMins()
                + (today.getOvertimeMinutes() != null ? today.getOvertimeMinutes() : 0));
        m.setTotalLateMins(m.getTotalLateMins()
                + (today.getLateMinutes()     != null ? today.getLateMinutes()     : 0));

        return m;
    }

    private AttendanceSummaryResponse toResponse(
            AttendanceSummary s, Employee emp, boolean isCurrentMonth) {

        String monthLabel = Month.of(s.getSummaryMonth())
                .getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + s.getSummaryYear();

        double present    = s.getPresentDays()  != null ? s.getPresentDays().doubleValue()  : 0.0;
        double absent     = s.getAbsentDays()   != null ? s.getAbsentDays().doubleValue()   : 0.0;
        double half       = s.getHalfDays()     != null ? s.getHalfDays().doubleValue()     : 0.0;
        double leave      = s.getLeaveDays()    != null ? s.getLeaveDays().doubleValue()     : 0.0;
        int    late       = s.getLateDays()     != null ? s.getLateDays()     : 0;
        int    holiday    = s.getHolidayDays()  != null ? s.getHolidayDays() : 0;
        int    weekend    = s.getWeekendDays()  != null ? s.getWeekendDays() : 0;

        // `present` already includes late arrivals, and `half` is stored as 0.5 per half-day,
        // so the number of half-day occurrences is half * 2. Days the employee was expected to
        // work = full-present days + half-day occurrences + absent days (leave/holiday/weekend
        // are not expected-work days).
        int totalWorkingDays = (int) Math.round(present + half * 2 + absent);

        // Attendance % = days attended (a half day counts 0.5) / expected days * 100
        double attendancePct = totalWorkingDays > 0
                ? BigDecimal.valueOf(((present + half) / totalWorkingDays) * 100)
                        .setScale(1, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        return new AttendanceSummaryResponse(
                s.getSummaryId(),
                s.getEmployeeId(),
                s.getEmployeeCode(),
                emp.getFirstName() + " " + emp.getLastName(),
                null,   // departmentName — resolved from job details if needed
                null,   // designationName
                s.getSummaryYear(),
                s.getSummaryMonth(),
                monthLabel,
                present, absent, half, late, leave, holiday, weekend,
                totalWorkingDays,
                s.getTotalWorkingMins()  != null ? s.getTotalWorkingMins()  : 0L,
                s.getTotalOvertimeMins() != null ? s.getTotalOvertimeMins() : 0L,
                s.getTotalLateMins()     != null ? s.getTotalLateMins()     : 0L,
                formatMinutes(s.getTotalWorkingMins()),
                formatMinutes(s.getTotalOvertimeMins()),
                formatMinutes(s.getTotalLateMins()),
                attendancePct,
                isCurrentMonth,
                s.getLastCalculatedAt()
        );
    }

    private AttendanceSummary buildEmptySummary(Employee emp, int year, int month) {
        AttendanceSummary s = AttendanceSummary.builder()
                .employeeId(emp.getId())
                .employeeCode(emp.getEmployeeCode())
                .summaryYear(year)
                .summaryMonth(month)
                .build();
        s.setCreatedAt(LocalDateTime.now());
        return s;
    }

    private Employee findEmployee(Long id) {
        return employeeRepo.findById(id)
                .orElseThrow(() -> {
                    log.error("Employee not found with id={}", id);
                    return new ResourceNotFoundException("Employee", "id", id);
                });
    }

    private String formatMinutes(Long totalMins) {
        if (totalMins == null || totalMins <= 0) return "0h 0m";
        return (totalMins / 60) + "h " + (totalMins % 60) + "m";
    }
}
