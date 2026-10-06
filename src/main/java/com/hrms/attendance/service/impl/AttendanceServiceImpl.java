package com.hrms.attendance.service.impl;

import com.hrms.attendance.dto.request.CheckInRequest;
import com.hrms.attendance.dto.request.CheckOutRequest;
import com.hrms.attendance.dto.response.AttendanceLogResponse;
import com.hrms.attendance.dto.response.AttendanceSummaryResponse;
import com.hrms.attendance.dto.response.DayRecordsResult;
import com.hrms.attendance.entity.AttendanceLog;
import com.hrms.attendance.enums.PunchSource;
import com.hrms.attendance.repository.AttendanceLogRepository;
import com.hrms.attendance.service.AttendanceService;
import com.hrms.attendance.service.AttendanceSummaryService;
import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.dto.PagedResponse;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.entity.WorkShift;
import com.hrms.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceServiceImpl implements AttendanceService {

    /** Most days an HR admin can regenerate in one call. */
    static final int MAX_REGENERATE_DAYS = 62;

    private final AttendanceLogRepository logRepo;
    private final EmployeeRepository      employeeRepo;
    private final AttendanceCalculator    calculator;
    private final AttendanceSummaryService summaryService;
    private final EmployeeAccessGuard     accessGuard;
    private final AttendanceDayClassifier classifier;
    private final AttendanceDayRecorder   recorder;

    // ────────────────────────────────────────────────────────
    // CHECK-IN
    // ────────────────────────────────────────────────────────
    @Override
    @Transactional
    public AttendanceLogResponse checkIn(CheckInRequest request) {
        accessGuard.assertSelfOrPrivileged(request.employeeId());

        Employee employee = findEmployee(request.employeeId());
        // Punch times are always server time — a client-supplied time would let anyone
        // back-date a check-in to dodge a late mark. Corrections go through regularization.
        LocalDateTime now = LocalDateTime.now();
        WorkShift shift   = calculator.resolveShift(employee.getId());
        // An overnight shift belongs to the day it started, so a punch after midnight is still that shift.
        LocalDate day     = calculator.attendanceDateFor(now, shift);

        // Weekend / holiday: only with approved overtime. Full-day approved leave: not at all.
        classifier.assertMayCheckIn(employee.getId(), shift, day);

        // The nightly job writes a placeholder row (no check-in) for days nobody punched.
        // Reuse it instead of inserting a second row for the same employee and date.
        AttendanceLog attendanceLog = logRepo
                .findByEmployeeIdAndAttendanceDateAndIsActive(employee.getId(), day, 1)
                .orElse(null);
        if (attendanceLog != null && attendanceLog.getCheckInTime() != null) {
            throw new BusinessRuleException(
                    "Employee " + employee.getEmployeeCode() + " has already checked in for " + day + ".");
        }
        boolean fresh = attendanceLog == null;
        if (fresh) {
            attendanceLog = AttendanceLog.builder()
                    .logId(logRepo.findNextSequenceValue())
                    .employeeId(employee.getId())
                    .employeeCode(employee.getEmployeeCode())
                    .attendanceDate(day)
                    .build();
            attendanceLog.setCreatedBy(employee.getEmployeeCode());
            attendanceLog.setCreatedAt(now);
        }
        attendanceLog.setPunchSource(request.punchSource() != null ? request.punchSource() : PunchSource.WEB);
        attendanceLog.setLocationId(request.locationId());
        attendanceLog.setNotes(request.notes());
        calculator.applyCheckIn(attendanceLog, now, shift, classifier.isNonWorkingDay(shift, day));
        attendanceLog.setUpdatedAt(now);

        return buildResponse(logRepo.save(attendanceLog), employee, shift);
    }

    // ────────────────────────────────────────────────────────
    // CHECK-OUT
    // ────────────────────────────────────────────────────────
    @Override
    @Transactional
    public AttendanceLogResponse checkOut(CheckOutRequest request) {
        accessGuard.assertSelfOrPrivileged(request.employeeId());

        Employee employee = findEmployee(request.employeeId());
        LocalDateTime now = LocalDateTime.now();
        WorkShift shift   = calculator.resolveShift(employee.getId());

        AttendanceLog attendanceLog = findCurrentLog(employee.getId(), shift, now)
                .orElseThrow(() -> new BusinessRuleException(
                        "No check-in found for today. Please check in first."));

        if (attendanceLog.getCheckInTime() == null) {
            throw new BusinessRuleException("No check-in found for today. Please check in first.");
        }
        if (attendanceLog.getCheckOutTime() != null) {
            throw new BusinessRuleException(
                    "Employee " + employee.getEmployeeCode() + " has already checked out for "
                            + attendanceLog.getAttendanceDate() + ".");
        }

        calculator.applyCheckOut(attendanceLog, now, shift,
                classifier.isNonWorkingDay(shift, attendanceLog.getAttendanceDate()));
        if (request.notes() != null) attendanceLog.setNotes(request.notes());
        attendanceLog.setUpdatedAt(now);

        return buildResponse(logRepo.save(attendanceLog), employee, shift);
    }

    // ────────────────────────────────────────────────────────
    // READ
    // ────────────────────────────────────────────────────────
    @Override
    public AttendanceLogResponse getTodayLog(Long employeeId) {
        accessGuard.assertSelfOrPrivileged(employeeId);
        Employee employee = findEmployee(employeeId);
        WorkShift shift   = calculator.resolveShift(employee.getId());
        return findCurrentLog(employee.getId(), shift, LocalDateTime.now())
                .map(l -> buildResponse(l, employee, shift))
                .orElse(null);
    }

    @Override
    public AttendanceLogResponse getLogByDate(Long employeeId, LocalDate date) {
        accessGuard.assertSelfOrPrivileged(employeeId);
        Employee employee = findEmployee(employeeId);
        WorkShift shift   = calculator.resolveShift(employee.getId());
        AttendanceLog log = logRepo
                .findByEmployeeIdAndAttendanceDateAndIsActive(employee.getId(), date, 1)
                .orElseThrow(() -> new ResourceNotFoundException("AttendanceLog", "date", date));
        return buildResponse(log, employee, shift);
    }

    @Override
    public List<AttendanceLogResponse> getMonthlyLogs(Long employeeId, int year, int month) {
        accessGuard.assertSelfOrPrivileged(employeeId);
        AttendanceCalculator.validateYearMonth(year, month);
        Employee employee = findEmployee(employeeId);
        WorkShift shift   = calculator.resolveShift(employee.getId());
        LocalDate from    = LocalDate.of(year, month, 1);
        LocalDate to      = from.withDayOfMonth(from.lengthOfMonth());
        return logRepo
                .findByEmployeeIdAndAttendanceDateBetweenAndIsActiveOrderByAttendanceDateAsc(
                        employee.getId(), from, to, 1)
                .stream()
                .map(l -> buildResponse(l, employee, shift))
                .collect(Collectors.toList());
    }

    @Override
    public PagedResponse<AttendanceLogResponse> getAllLogs(
            Long employeeId, LocalDate from, LocalDate to, Pageable pageable) {
        Page<AttendanceLog> page = logRepo.findByFilters(employeeId, from, to, pageable);

        // Resolve each employee and shift once per page instead of once per row.
        Set<Long> ids = page.getContent().stream()
                .map(AttendanceLog::getEmployeeId).collect(Collectors.toSet());
        Map<Long, Employee> employees = employeeRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));
        Map<Long, WorkShift> shifts = new HashMap<>();

        return PagedResponse.from(page.map(l -> {
            Employee emp = employees.get(l.getEmployeeId());
            if (emp == null) throw new ResourceNotFoundException("Employee", "id", l.getEmployeeId());
            WorkShift shift = shifts.computeIfAbsent(emp.getId(), calculator::resolveShift);
            return buildResponse(l, emp, shift);
        }));
    }

    // ────────────────────────────────────────────────────────
    // MONTHLY SUMMARY — delegates to the summary service (single implementation)
    // ────────────────────────────────────────────────────────
    @Override
    public AttendanceSummaryResponse getMonthlySummary(Long employeeId, int year, int month) {
        return summaryService.getEmployeeSummary(employeeId, year, month);
    }

    // ────────────────────────────────────────────────────────
    // NIGHTLY SCHEDULER
    // ────────────────────────────────────────────────────────
    // Deliberately not @Transactional: the day's rows commit in recorder.generateFor, and each
    // employee's summary in its own transaction, so one failing summary cannot roll back the rest.
    @Override
    public void calculateAndStoreDailySummary(LocalDate date) {
        log.info("Running nightly attendance aggregation for: {}", date);
        // A finished day also gets a row for everyone who did not punch (absent / weekend /
        // holiday / leave); today is still in progress, so it only aggregates what exists.
        recorder.generateFor(date);
        Set<Long> employeeIds = logRepo.findByAttendanceDateAndIsActive(date, 1).stream()
                .map(AttendanceLog::getEmployeeId).collect(Collectors.toSet());

        for (Long empId : employeeIds) {
            try {
                summaryService.recalculateSummary(empId, date.getYear(), date.getMonthValue());
            } catch (Exception e) {
                log.error("Summary failed for employee {}: {}", empId, e.getMessage());
            }
        }
    }

    // Not @Transactional, same reason as calculateAndStoreDailySummary.
    @Override
    public DayRecordsResult regenerateDayRecords(LocalDate from, LocalDate to) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        if (from.isAfter(to)) {
            throw new BusinessRuleException("INVALID_RANGE", "The start date must not be after the end date.");
        }
        if (to.isAfter(yesterday)) {
            throw new BusinessRuleException("DAY_NOT_OVER", "Records can only be generated for days that are over.");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) >= MAX_REGENERATE_DAYS) {
            throw new BusinessRuleException("RANGE_TOO_LONG",
                    "Generate at most " + MAX_REGENERATE_DAYS + " days at a time.");
        }

        int created = 0;
        int updated = 0;
        // One summary refresh per employee and month, however many days changed.
        Map<Long, Set<java.time.YearMonth>> affected = new HashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            AttendanceDayRecorder.Result r = recorder.generateFor(d);
            created += r.created();
            updated += r.updated();
            // Everyone with a row that day (punched, regularized or generated): the month total
            // only picks a day up once it is over, so the day that just ended needs a refresh too.
            for (AttendanceLog l : logRepo.findByAttendanceDateAndIsActive(d, 1)) {
                affected.computeIfAbsent(l.getEmployeeId(), k -> new java.util.HashSet<>())
                        .add(java.time.YearMonth.from(d));
            }
        }
        affected.forEach((empId, months) -> months.forEach(ym -> {
            try {
                summaryService.recalculateSummary(empId, ym.getYear(), ym.getMonthValue());
            } catch (Exception e) {
                log.error("Summary failed for employee {} {}: {}", empId, ym, e.getMessage());
            }
        }));
        log.info("Day records {}..{}: {} created, {} corrected, {} employees refreshed",
                from, to, created, updated, affected.size());
        return new DayRecordsResult(created, updated, affected.size());
    }

    // ── Private helpers ───────────────────────────────────────

    /**
     * The log a punch at {@code now} belongs to: for an overnight shift the open log of the shift
     * that started yesterday (so a check-out after midnight finds it), otherwise today's.
     */
    private java.util.Optional<AttendanceLog> findCurrentLog(Long employeeId, WorkShift shift, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        if (AttendanceCalculator.isOvernight(shift)) {
            java.util.Optional<AttendanceLog> open = logRepo
                    .findByEmployeeIdAndAttendanceDateAndIsActive(employeeId, today.minusDays(1), 1)
                    .filter(l -> l.getCheckInTime() != null && l.getCheckOutTime() == null)
                    .filter(l -> java.time.Duration.between(l.getCheckInTime(), now).toHours() < 24);
            if (open.isPresent()) return open;
        }
        return logRepo.findByEmployeeIdAndAttendanceDateAndIsActive(employeeId, today, 1);
    }

    private AttendanceLogResponse buildResponse(
            AttendanceLog l, Employee emp, WorkShift shift) {
        return new AttendanceLogResponse(
                l.getLogId(), l.getEmployeeId(), l.getEmployeeCode(),
                emp.getFirstName() + " " + emp.getLastName(),
                l.getAttendanceDate(), l.getCheckInTime(), l.getCheckOutTime(),
                l.getWorkingMinutes(), l.getOvertimeMinutes(),
                l.getLateMinutes(), l.getEarlyLeaveMinutes(),
                l.getStatus(), l.getPunchSource(),
                l.getLocationId(), null,
                shift != null ? shift.getShiftName()  : null,
                shift != null ? shift.getStartTime()  : null,
                shift != null ? shift.getEndTime()    : null,
                l.getNotes(), l.getIsRegularized() == 1,
                l.getCreatedAt(), l.getUpdatedAt());
    }

    private Employee findEmployee(Long id) {
        return employeeRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", id));
    }
}
