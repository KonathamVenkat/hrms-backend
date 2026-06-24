package com.hrms.attendance.service.impl;

import com.hrms.attendance.dto.request.CheckInRequest;
import com.hrms.attendance.dto.request.CheckOutRequest;
import com.hrms.attendance.dto.response.AttendanceLogResponse;
import com.hrms.attendance.dto.response.AttendanceSummaryResponse;
import com.hrms.attendance.entity.AttendanceLog;
import com.hrms.attendance.entity.AttendanceSummary;
import com.hrms.attendance.enums.AttendanceStatus;
import com.hrms.attendance.enums.PunchSource;
import com.hrms.attendance.mapper.AttendanceMapper;
import com.hrms.attendance.repository.AttendanceLogRepository;
import com.hrms.attendance.repository.AttendanceSummaryRepository;
import com.hrms.attendance.service.AttendanceService;
import com.hrms.common.dto.PagedResponse;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.entity.EmployeeJobDetails;
import com.hrms.employee.entity.WorkShift;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.repository.WorkShiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceLogRepository      logRepo;
    private final AttendanceSummaryRepository  summaryRepo;
    private final EmployeeRepository           employeeRepo;
    private final EmployeeJobDetailsRepository jobDetailsRepo;
    private final WorkShiftRepository          workShiftRepo;   // ← injected to resolve shift
    private final AttendanceMapper             mapper;

    // ── Oman weekend: Friday + Saturday ──────────────────────
    private static final java.util.Set<java.time.DayOfWeek> WEEKENDS =
            java.util.Set.of(java.time.DayOfWeek.FRIDAY, java.time.DayOfWeek.SATURDAY);

    // ────────────────────────────────────────────────────────
    // CHECK-IN
    // ────────────────────────────────────────────────────────
    @Override
    @Transactional
    public AttendanceLogResponse checkIn(CheckInRequest request) {
        Employee employee  = findEmployee(request.employeeId());
        LocalDate today    = LocalDate.now();
        LocalDateTime now  = request.checkInTime() != null
                ? LocalDateTime.parse(request.checkInTime())
                : LocalDateTime.now();

        // Prevent duplicate check-in
        logRepo.findByEmployeeIdAndAttendanceDateAndIsActive(employee.getId(), today, 1)
                .ifPresent(existing -> {
                    if (existing.getCheckInTime() != null) {
                        throw new BusinessRuleException(
                                "Employee " + employee.getEmployeeCode()
                                        + " has already checked in today.");
                    }
                });

        // Resolve shift via job details → shiftId → WorkShift
        EmployeeJobDetails jobDetails = getCurrentJobDetails(employee.getId());
        WorkShift shift               = resolveShift(jobDetails);

        int lateMinutes      = 0;
        AttendanceStatus status = AttendanceStatus.PRESENT;

        if (shift != null) {
            LocalDateTime shiftStartDt = today.atTime(parseTime(shift.getStartTime()));
            LocalDateTime allowedStart = shiftStartDt.plusMinutes(shift.getGracePeriod());
            if (now.isAfter(allowedStart)) {
                lateMinutes = (int) java.time.Duration.between(shiftStartDt, now).toMinutes();
                status      = AttendanceStatus.LATE;
            }
        }

        Long logId = logRepo.findNextSequenceValue();

        AttendanceLog attendanceLog = AttendanceLog.builder()
                .logId(logId)
                .employeeId(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .attendanceDate(today)
                .checkInTime(now)
                .status(status)
                .lateMinutes(lateMinutes)
                .punchSource(request.punchSource() != null ? request.punchSource() : PunchSource.WEB)
                .locationId(request.locationId())
                .notes(request.notes())
                .build();

        attendanceLog.setCreatedBy(employee.getEmployeeCode());
        attendanceLog.setCreatedAt(LocalDateTime.now());
        attendanceLog.setUpdatedAt(LocalDateTime.now());

        return buildResponse(logRepo.save(attendanceLog), employee, shift);
    }

    // ────────────────────────────────────────────────────────
    // CHECK-OUT
    // ────────────────────────────────────────────────────────
    @Override
    @Transactional
    public AttendanceLogResponse checkOut(CheckOutRequest request) {
        Employee employee  = findEmployee(request.employeeId());
        LocalDate today    = LocalDate.now();
        LocalDateTime now  = request.checkOutTime() != null
                ? LocalDateTime.parse(request.checkOutTime())
                : LocalDateTime.now();

        AttendanceLog attendanceLog = logRepo
                .findByEmployeeIdAndAttendanceDateAndIsActive(employee.getId(), today, 1)
                .orElseThrow(() -> new BusinessRuleException(
                        "No check-in found for today. Please check in first."));

        if (attendanceLog.getCheckOutTime() != null) {
            throw new BusinessRuleException(
                    "Employee " + employee.getEmployeeCode() + " has already checked out today.");
        }
        if (now.isBefore(attendanceLog.getCheckInTime())) {
            throw new BusinessRuleException("Check-out time cannot be before check-in time.");
        }

        int workingMins    = (int) java.time.Duration
                .between(attendanceLog.getCheckInTime(), now).toMinutes();
        int overtimeMins   = 0;
        int earlyLeaveMins = 0;

        EmployeeJobDetails jobDetails = getCurrentJobDetails(employee.getId());
        WorkShift shift               = resolveShift(jobDetails);

        if (shift != null) {
            int expectedMins = (int)(shift.getWorkingHours().doubleValue() * 60);
            if (workingMins > expectedMins) {
                overtimeMins   = workingMins - expectedMins;
            } else if (workingMins < expectedMins) {
                earlyLeaveMins = expectedMins - workingMins;
            }
        }

        attendanceLog.setCheckOutTime(now);
        attendanceLog.setWorkingMinutes(workingMins);
        attendanceLog.setOvertimeMinutes(overtimeMins);
        attendanceLog.setEarlyLeaveMinutes(earlyLeaveMins);
        attendanceLog.setStatus(resolveCheckOutStatus(
                attendanceLog.getStatus(), workingMins, shift));
        if (request.notes() != null) attendanceLog.setNotes(request.notes());
        attendanceLog.setUpdatedAt(LocalDateTime.now());

        return buildResponse(logRepo.save(attendanceLog), employee, shift);
    }

    // ────────────────────────────────────────────────────────
    // READ
    // ────────────────────────────────────────────────────────
    @Override
    public AttendanceLogResponse getTodayLog(Long employeeId) {
        Employee employee  = findEmployee(employeeId);
        WorkShift shift    = resolveShift(getCurrentJobDetails(employee.getId()));
        return logRepo
                .findByEmployeeIdAndAttendanceDateAndIsActive(employee.getId(), LocalDate.now(), 1)
                .map(l -> buildResponse(l, employee, shift))
                .orElse(null);
    }

    @Override
    public AttendanceLogResponse getLogByDate(Long employeeId, LocalDate date) {
        Employee employee  = findEmployee(employeeId);
        WorkShift shift    = resolveShift(getCurrentJobDetails(employee.getId()));
        AttendanceLog log  = logRepo
                .findByEmployeeIdAndAttendanceDateAndIsActive(employee.getId(), date, 1)
                .orElseThrow(() -> new ResourceNotFoundException("AttendanceLog", "date", date));
        return buildResponse(log, employee, shift);
    }

    @Override
    public List<AttendanceLogResponse> getMonthlyLogs(Long employeeId, int year, int month) {
        Employee employee  = findEmployee(employeeId);
        WorkShift shift    = resolveShift(getCurrentJobDetails(employee.getId()));
        LocalDate from     = LocalDate.of(year, month, 1);
        LocalDate to       = from.withDayOfMonth(from.lengthOfMonth());
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
        return PagedResponse.from(page.map(l -> {
            Employee emp   = findEmployee(l.getEmployeeId());
            WorkShift shft = resolveShift(getCurrentJobDetails(emp.getId()));
            return buildResponse(l, emp, shft);
        }));
    }

    // ────────────────────────────────────────────────────────
    // MONTHLY SUMMARY — HYBRID
    // ────────────────────────────────────────────────────────
    @Override
    public AttendanceSummaryResponse getMonthlySummary(Long employeeId, int year, int month) {
        Employee employee  = findEmployee(employeeId);
        LocalDate now      = LocalDate.now();
        boolean isCurrent  = (now.getYear() == year && now.getMonthValue() == month);

        AttendanceSummary stored = summaryRepo
                .findByEmployeeIdAndSummaryYearAndSummaryMonth(employee.getId(), year, month)
                .orElse(buildEmptySummary(employee, year, month));

        if (!isCurrent) {
            return mapper.toSummaryResponse(stored,
                    employee.getFirstName() + " " + employee.getLastName(), false);
        }

        Optional<AttendanceLog> todayLog = logRepo
                .findByEmployeeIdAndAttendanceDateAndIsActive(employee.getId(), now, 1);

        AttendanceSummary merged = mergeTodayIntoSummary(stored, todayLog.orElse(null));
        return mapper.toSummaryResponse(merged,
                employee.getFirstName() + " " + employee.getLastName(), true);
    }

    // ────────────────────────────────────────────────────────
    // NIGHTLY SCHEDULER
    // ────────────────────────────────────────────────────────
    @Override
    @Transactional
    public void calculateAndStoreDailySummary(LocalDate date) {
        log.info("Running nightly attendance aggregation for: {}", date);
        List<AttendanceLog> logs = logRepo.findByAttendanceDateAndIsActive(date, 1);
        Map<Long, List<AttendanceLog>> byEmployee = logs.stream()
                .collect(Collectors.groupingBy(AttendanceLog::getEmployeeId));

        for (Long empId : byEmployee.keySet()) {
            try {
                upsertSummaryForEmployee(empId, date.getYear(), date.getMonthValue());
            } catch (Exception e) {
                log.error("Summary failed for employee {}: {}", empId, e.getMessage());
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────

    /**
     * Resolves WorkShift from EmployeeJobDetails.shiftId.
     * EmployeeJobDetails stores shiftId as a plain Long FK (no @ManyToOne),
     * so we look up WorkShift separately via WorkShiftRepository.
     */
    private WorkShift resolveShift(EmployeeJobDetails jobDetails) {
        if (jobDetails == null || jobDetails.getShiftId() == null) return null;
        return workShiftRepo.findById(jobDetails.getShiftId()).orElse(null);
    }

    private void upsertSummaryForEmployee(Long empId, int year, int month) {
        Employee employee  = findEmployee(empId);
        LocalDate from     = LocalDate.of(year, month, 1);
        LocalDate to       = LocalDate.now().minusDays(1);
        LocalDate monthEnd = from.withDayOfMonth(from.lengthOfMonth());
        if (to.isAfter(monthEnd)) to = monthEnd;

        List<AttendanceLog> logs = logRepo
                .findByEmployeeIdAndAttendanceDateBetweenAndIsActiveOrderByAttendanceDateAsc(
                        employee.getId(), from, to, 1);

        BigDecimal presentDays = BigDecimal.ZERO;
        BigDecimal absentDays  = BigDecimal.ZERO;
        BigDecimal halfDays    = BigDecimal.ZERO;
        BigDecimal leaveDays   = BigDecimal.ZERO;
        int    lateDays = 0, holidayDays = 0, weekendDays = 0;
        long   workMins = 0, otMins = 0, lateMins = 0;

        for (AttendanceLog l : logs) {
            switch (l.getStatus()) {
                case PRESENT  -> presentDays = presentDays.add(BigDecimal.ONE);
                case LATE     -> { presentDays = presentDays.add(BigDecimal.ONE); lateDays++; }
                case ABSENT   -> absentDays  = absentDays.add(BigDecimal.ONE);
                case HALF_DAY -> halfDays    = halfDays.add(new BigDecimal("0.5"));
                case ON_LEAVE -> leaveDays   = leaveDays.add(BigDecimal.ONE);
                case HOLIDAY  -> holidayDays++;
                case WEEKEND  -> weekendDays++;
            }
            workMins += l.getWorkingMinutes()  != null ? l.getWorkingMinutes()  : 0;
            otMins   += l.getOvertimeMinutes() != null ? l.getOvertimeMinutes() : 0;
            lateMins += l.getLateMinutes()     != null ? l.getLateMinutes()     : 0;
        }

        AttendanceSummary summary = summaryRepo
                .findByEmployeeIdAndSummaryYearAndSummaryMonth(employee.getId(), year, month)
                .orElseGet(() -> {
                    Long id = summaryRepo.findNextSequenceValue();
                    AttendanceSummary s = AttendanceSummary.builder()
                            .summaryId(id)
                            .employeeId(employee.getId())
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

        summaryRepo.save(summary);
    }

    private AttendanceSummary mergeTodayIntoSummary(
            AttendanceSummary base, AttendanceLog today) {

        if (today == null) return base;

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

    private AttendanceSummary buildEmptySummary(Employee emp, int year, int month) {
        return AttendanceSummary.builder()
                .employeeId(emp.getId())
                .employeeCode(emp.getEmployeeCode())
                .summaryYear(year)
                .summaryMonth(month)
                .build();
    }

    private AttendanceStatus resolveCheckOutStatus(
            AttendanceStatus current, int workingMins, WorkShift shift) {
        if (current == AttendanceStatus.LATE) return AttendanceStatus.LATE;
        if (shift == null)                    return AttendanceStatus.PRESENT;
        int halfThreshold = (int)(shift.getWorkingHours().doubleValue() * 60 / 2);
        return workingMins < halfThreshold ? AttendanceStatus.HALF_DAY : AttendanceStatus.PRESENT;
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

    private EmployeeJobDetails getCurrentJobDetails(Long employeeId) {
        return jobDetailsRepo.findByEmployeeIdAndIsCurrent(employeeId, 1).orElse(null);
    }

    private java.time.LocalTime parseTime(String hhmm) {
        return java.time.LocalTime.parse(hhmm, DateTimeFormatter.ofPattern("HH:mm"));
    }
}