package com.hrms.attendance.service.impl;

import com.hrms.attendance.dto.request.CheckInRequest;
import com.hrms.attendance.dto.request.CheckOutRequest;
import com.hrms.attendance.dto.response.AttendanceLogResponse;
import com.hrms.attendance.dto.response.AttendanceSummaryResponse;
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

    private final AttendanceLogRepository logRepo;
    private final EmployeeRepository      employeeRepo;
    private final AttendanceCalculator    calculator;
    private final AttendanceSummaryService summaryService;
    private final EmployeeAccessGuard     accessGuard;

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
        LocalDate today   = now.toLocalDate();

        logRepo.findByEmployeeIdAndAttendanceDateAndIsActive(employee.getId(), today, 1)
                .ifPresent(existing -> {
                    if (existing.getCheckInTime() != null) {
                        throw new BusinessRuleException(
                                "Employee " + employee.getEmployeeCode()
                                        + " has already checked in today.");
                    }
                });

        WorkShift shift = calculator.resolveShift(employee.getId());

        AttendanceLog attendanceLog = AttendanceLog.builder()
                .logId(logRepo.findNextSequenceValue())
                .employeeId(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .attendanceDate(today)
                .punchSource(request.punchSource() != null ? request.punchSource() : PunchSource.WEB)
                .locationId(request.locationId())
                .notes(request.notes())
                .build();
        calculator.applyCheckIn(attendanceLog, now, shift);

        attendanceLog.setCreatedBy(employee.getEmployeeCode());
        attendanceLog.setCreatedAt(now);
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

        AttendanceLog attendanceLog = logRepo
                .findByEmployeeIdAndAttendanceDateAndIsActive(employee.getId(), now.toLocalDate(), 1)
                .orElseThrow(() -> new BusinessRuleException(
                        "No check-in found for today. Please check in first."));

        if (attendanceLog.getCheckInTime() == null) {
            throw new BusinessRuleException("No check-in found for today. Please check in first.");
        }
        if (attendanceLog.getCheckOutTime() != null) {
            throw new BusinessRuleException(
                    "Employee " + employee.getEmployeeCode() + " has already checked out today.");
        }

        WorkShift shift = calculator.resolveShift(employee.getId());
        calculator.applyCheckOut(attendanceLog, now, shift);
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
        return logRepo
                .findByEmployeeIdAndAttendanceDateAndIsActive(employee.getId(), LocalDate.now(), 1)
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
    @Override
    @Transactional
    public void calculateAndStoreDailySummary(LocalDate date) {
        log.info("Running nightly attendance aggregation for: {}", date);
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

    // ── Private helpers ───────────────────────────────────────

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
