package com.hrms.attendance.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.attendance.dto.request.OvertimeActionRequest;
import com.hrms.attendance.dto.request.OvertimeSubmitRequest;
import com.hrms.attendance.dto.response.OvertimeResponse;
import com.hrms.attendance.entity.AttendanceLog;
import com.hrms.attendance.entity.OvertimeRequest;
import com.hrms.attendance.enums.OvertimeType;
import com.hrms.attendance.enums.RegularizationStatus;
import com.hrms.attendance.repository.AttendanceLogRepository;
import com.hrms.attendance.repository.OvertimeRequestRepository;
import com.hrms.attendance.service.OvertimeRequestService;
import com.hrms.attendance.service.AttendanceSummaryService;
import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.employee.entity.WorkShift;
import com.hrms.leave.service.WorkCalendar;
import com.hrms.common.dto.PagedResponse;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OvertimeRequestServiceImpl implements OvertimeRequestService {

    private final OvertimeRequestRepository  otRepo;
    private final AttendanceLogRepository    logRepo;
    private final EmployeeRepository         employeeRepo;
    private final EmployeeAccessGuard        accessGuard;
    private final AttendanceCalculator       calculator;
    private final AttendanceSummaryService   summaryService;
    private final WorkCalendar               workCalendar;
    private final AuditTrail audit;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("EEE, MMM d yyyy");

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    /** Upper bound for a single day's overtime request. */
    private static final int MAX_OT_MINUTES = 12 * 60;

    // ────────────────────────────────────────────────────
    // SUBMIT
    // ────────────────────────────────────────────────────
    @Override
    @Transactional
    public OvertimeResponse submit(OvertimeSubmitRequest request) {
        log.info("Overtime submit — employeeId={}, date={}, type={}",
                request.employeeId(), request.otDate(), request.otType());

        accessGuard.assertSelfOrPrivileged(request.employeeId());
        Employee employee = findEmployee(request.employeeId());
        LocalDate otDate  = LocalDate.parse(request.otDate());

        // ── Business rules ────────────────────────────────
        // 1. Cannot submit for future dates (except PRE_APPROVED)
        if (request.otType() != OvertimeType.PRE_APPROVED
                && otDate.isAfter(LocalDate.now())) {
            throw new BusinessRuleException(
                    "Post-facto overtime cannot be submitted for a future date. "
                    + "Use PRE_APPROVED type for upcoming overtime.");
        }

        // 2. Cannot submit older than 30 days
        if (otDate.isBefore(LocalDate.now().minusDays(30))) {
            throw new BusinessRuleException(
                    "Overtime request can only be submitted for dates within the last 30 days.");
        }

        // 3. Duplicate pending check
        boolean hasPending = otRepo.existsByEmployeeIdAndOtDateAndStatusAndIsActive(
                employee.getId(), otDate, RegularizationStatus.PENDING, 1);
        if (hasPending) {
            log.warn("Duplicate OT request — employeeId={}, date={}",
                    employee.getId(), otDate);
            throw new BusinessRuleException(
                    "A pending overtime request already exists for " + otDate);
        }

        // 4. Parse and validate times
        LocalDateTime startTime = LocalDateTime.parse(request.startTime());
        LocalDateTime endTime   = LocalDateTime.parse(request.endTime());

        if (!endTime.isAfter(startTime)) {
            throw new BusinessRuleException("End time must be after start time.");
        }

        int durationMins = (int) java.time.Duration
                .between(startTime, endTime).toMinutes();

        if (durationMins < 30) {
            throw new BusinessRuleException(
                    "Overtime request must be for at least 30 minutes.");
        }
        // Daily cap counts everything already requested for that date (pending + approved)
        Long alreadyRequested = otRepo.sumPendingAndApprovedMinutes(employee.getId(), otDate);
        long dayTotal = durationMins + (alreadyRequested != null ? alreadyRequested : 0L);
        if (dayTotal > MAX_OT_MINUTES) {
            throw new BusinessRuleException("OT_DAILY_LIMIT",
                    "Overtime cannot exceed " + (MAX_OT_MINUTES / 60) + " hours in a day. "
                    + "Already requested for " + otDate + ": "
                    + (alreadyRequested != null ? alreadyRequested : 0L) + " minutes.");
        }

        // 5. The worked window must belong to the OT date (it may run past midnight)
        if (!startTime.toLocalDate().equals(otDate)) {
            throw new BusinessRuleException(
                    "Overtime start time must be on the overtime date " + otDate + ".");
        }
        if (!endTime.toLocalDate().equals(otDate) && !endTime.toLocalDate().equals(otDate.plusDays(1))) {
            throw new BusinessRuleException(
                    "Overtime end time must be on " + otDate + " or the following day.");
        }
        if (request.otType() != OvertimeType.PRE_APPROVED && endTime.isAfter(LocalDateTime.now())) {
            throw new BusinessRuleException(
                    "Post-facto overtime cannot end in the future. "
                    + "Use PRE_APPROVED type for upcoming overtime.");
        }

        // 6. Regular shift hours are not overtime
        assertOutsideShiftHours(employee, otDate, startTime, endTime);

        // ── Generate OT_ID in Java before persist ──────────
        // Hibernate requires non-null String PK before save().
        // Fetch sequence nextval and format as "OT-YYYY-NNNNNN".
        Long seqVal = otRepo.findNextSequenceValue();
        String otId = "OT-"
                + java.time.Year.now().getValue()
                + "-"
                + String.format("%06d", seqVal);

        log.debug("Generated otId={}", otId);

        // ── Build and persist ─────────────────────────────
        OvertimeRequest otRequest = OvertimeRequest.builder()
                .otId(otId)
                .employeeId(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .otDate(otDate)
                .otType(request.otType())
                .startTime(startTime)
                .endTime(endTime)
                .durationMinutes(durationMins)
                .reason(request.reason())
                .projectCode(request.projectCode())
                .status(RegularizationStatus.PENDING)
                .build();

        otRequest.setCreatedBy(employee.getEmployeeCode());
        otRequest.setCreatedAt(LocalDateTime.now());
        otRequest.setUpdatedAt(LocalDateTime.now());

        OvertimeRequest saved = otRepo.save(otRequest);

        log.info("Overtime submitted successfully — otId={}, employeeId={}, duration={}m",
                saved.getOtId(), employee.getId(), durationMins);

        return toResponse(saved, employee, null);
    }

    // ────────────────────────────────────────────────────
    // CANCEL
    // ────────────────────────────────────────────────────
    @Override
    @Transactional
    public OvertimeResponse cancel(String otId, Long employeeId) {
        log.info("Overtime cancel — otId={}, employeeId={}", otId, employeeId);

        OvertimeRequest otRequest = findOtRequest(otId);
        accessGuard.assertSelfOrPrivileged(employeeId);
        Employee employee = findEmployee(employeeId);

        if (!otRequest.getEmployeeId().equals(employeeId)) {
            throw new BusinessRuleException(
                    "You can only cancel your own overtime requests.");
        }

        if (otRequest.getStatus() != RegularizationStatus.PENDING) {
            throw new BusinessRuleException(
                    "Only PENDING requests can be cancelled. Current status: "
                    + otRequest.getStatus());
        }

        otRequest.setStatus(RegularizationStatus.CANCELLED);
        otRequest.setUpdatedAt(LocalDateTime.now());
        otRequest.setUpdatedBy(employee.getEmployeeCode());

        OvertimeRequest saved = otRepo.save(otRequest);
        log.info("Overtime cancelled — otId={}", otId);

        return toResponse(saved, employee, null);
    }

    // ────────────────────────────────────────────────────
    // APPROVE
    // ────────────────────────────────────────────────────
    @Override
    @Transactional
    public OvertimeResponse approve(String otId, OvertimeActionRequest request) {
        log.info("Overtime approve — otId={}", otId);

        OvertimeRequest otRequest = findOtRequest(otId);
        validatePendingStatus(otRequest);

        Employee employee = findEmployee(otRequest.getEmployeeId());
        Employee reviewer = resolveReviewer(otRequest);

        otRequest.setStatus(RegularizationStatus.APPROVED);
        otRequest.setReviewedBy(reviewer.getId());
        otRequest.setReviewedAt(LocalDateTime.now());
        otRequest.setUpdatedBy(reviewer.getEmployeeCode());
        otRequest.setUpdatedAt(LocalDateTime.now());

        // ── Update attendance log overtime minutes ────────
        updateAttendanceLogOvertime(otRequest, employee);

        OvertimeRequest saved = otRepo.save(otRequest);
        audit.record("OVERTIME_APPROVED", "OVERTIME_REQUEST", otId, "employee " + employee.getId());
        log.info("Overtime approved — otId={}, employee={}, duration={}m",
                otId, employee.getId(), otRequest.getDurationMinutes());

        return toResponse(saved, employee, reviewer);
    }

    // ────────────────────────────────────────────────────
    // REJECT
    // ────────────────────────────────────────────────────
    @Override
    @Transactional
    public OvertimeResponse reject(String otId, OvertimeActionRequest request) {
        log.info("Overtime reject — otId={}", otId);

        if (request.rejectionReason() == null || request.rejectionReason().isBlank()) {
            throw new BusinessRuleException(
                    "Rejection reason is required when rejecting an overtime request.");
        }

        OvertimeRequest otRequest = findOtRequest(otId);
        validatePendingStatus(otRequest);

        Employee reviewer = resolveReviewer(otRequest);

        otRequest.setStatus(RegularizationStatus.REJECTED);
        otRequest.setRejectionReason(request.rejectionReason());
        otRequest.setReviewedBy(reviewer.getId());
        otRequest.setReviewedAt(LocalDateTime.now());
        otRequest.setUpdatedBy(reviewer.getEmployeeCode());
        otRequest.setUpdatedAt(LocalDateTime.now());

        OvertimeRequest saved = otRepo.save(otRequest);
        audit.record("OVERTIME_REJECTED", "OVERTIME_REQUEST", otId,
            "employee " + otRequest.getEmployeeId() + ", reason: " + request.rejectionReason());
        log.info("Overtime rejected — otId={}", otId);

        Employee employee = findEmployee(otRequest.getEmployeeId());
        return toResponse(saved, employee, reviewer);
    }

    // ────────────────────────────────────────────────────
    // READ OPERATIONS
    // ────────────────────────────────────────────────────
    @Override
    public PagedResponse<OvertimeResponse> getMyRequests(
            Long employeeId, Pageable pageable) {
        log.info("Fetching OT requests — employeeId={}, page={}",
                employeeId, pageable.getPageNumber());

        accessGuard.assertSelfOrPrivileged(employeeId);
        Employee employee = findEmployee(employeeId);
        Page<OvertimeRequest> page = otRepo
                .findByEmployeeIdAndIsActiveOrderByCreatedAtDesc(
                        employeeId, 1, pageable);

        log.info("Found {} OT requests for employeeId={}",
                page.getTotalElements(), employeeId);

        return PagedResponse.from(page.map(r -> toResponse(r, employee, null)));
    }

    @Override
    public OvertimeResponse getById(String otId) {
        log.info("Fetching OT request by id={}", otId);
        OvertimeRequest otRequest = findOtRequest(otId);
        accessGuard.assertSelfOrPrivileged(otRequest.getEmployeeId());
        Employee employee = findEmployee(otRequest.getEmployeeId());
        Employee reviewer = otRequest.getReviewedBy() != null
                ? findEmployee(otRequest.getReviewedBy()) : null;
        return toResponse(otRequest, employee, reviewer);
    }

    @Override
    public PagedResponse<OvertimeResponse> getAllRequests(
            RegularizationStatus status,
            Long employeeId,
            LocalDate from,
            LocalDate to,
            Pageable pageable) {

        log.info("Fetching all OT requests — status={}, employeeId={}, from={}, to={}",
                status, employeeId, from, to);

        Page<OvertimeRequest> page = otRepo
                .findByFilters(status, employeeId, from, to, pageable);

        log.info("Found {} total OT requests", page.getTotalElements());

        return PagedResponse.from(page.map(r -> {
            Employee emp      = findEmployee(r.getEmployeeId());
            Employee reviewer = r.getReviewedBy() != null
                    ? findEmployee(r.getReviewedBy()) : null;
            return toResponse(r, emp, reviewer);
        }));
    }

    @Override
    public long getPendingCount() {
        long count = otRepo.countByStatusAndIsActive(
                RegularizationStatus.PENDING, 1);
        log.debug("Pending OT count: {}", count);
        return count;
    }

    // ── Private helpers ───────────────────────────────────

    /**
     * On approval, reflect the approved overtime on the day's attendance log. The log's
     * overtime is the LARGER of the time already detected at check-out and the total approved
     * for that date — never their sum, which would count the same hours twice. If there is no
     * log yet (e.g. PRE_APPROVED overtime for a future date) nothing is written now: check-out
     * folds in the approved minutes via {@link AttendanceCalculator}.
     */
    private void updateAttendanceLogOvertime(
            OvertimeRequest otRequest, Employee employee) {

        // Flush this approval first so the approved-minutes sum below includes it.
        otRepo.saveAndFlush(otRequest);

        Optional<AttendanceLog> logOpt = logRepo
                .findByEmployeeIdAndAttendanceDateAndIsActive(
                        employee.getId(), otRequest.getOtDate(), 1);

        if (logOpt.isEmpty()) {
            log.info("No attendance log yet for employeeId={} on OT date={}; approved overtime "
                    + "will be applied at check-out.", employee.getId(), otRequest.getOtDate());
            return;
        }

        AttendanceLog attendanceLog = logOpt.get();
        Long approvedTotal = otRepo.sumApprovedMinutes(employee.getId(), otRequest.getOtDate());
        int current = attendanceLog.getOvertimeMinutes() != null ? attendanceLog.getOvertimeMinutes() : 0;
        int updated = (int) Math.max(current, approvedTotal != null ? approvedTotal : 0L);
        if (updated != current) {
            attendanceLog.setOvertimeMinutes(updated);
            attendanceLog.setUpdatedAt(LocalDateTime.now());
            logRepo.save(attendanceLog);
        }
        log.info("Attendance log OT — logId={}, totalOtMins={}", attendanceLog.getLogId(), updated);

        // Past days are already baked into the stored monthly summary — refresh it.
        if (otRequest.getOtDate().isBefore(LocalDate.now())) {
            summaryService.recalculateSummary(employee.getId(),
                    otRequest.getOtDate().getYear(), otRequest.getOtDate().getMonthValue());
        }
    }

    /** Overtime must fall outside the employee's regular shift; weekends and public holidays are all-overtime days. */
    private void assertOutsideShiftHours(
            Employee employee, LocalDate otDate, LocalDateTime start, LocalDateTime end) {

        WorkShift shift = calculator.resolveShift(employee.getId());
        if (workCalendar.isNonWorkingDay(shift, otDate)) return;
        if (shift == null || shift.getStartTime() == null || shift.getEndTime() == null) return;

        LocalDateTime shiftStart = otDate.atTime(java.time.LocalTime.parse(shift.getStartTime(), HHMM));
        LocalDateTime shiftEnd   = otDate.atTime(java.time.LocalTime.parse(shift.getEndTime(), HHMM));
        if (!shiftEnd.isAfter(shiftStart)) shiftEnd = shiftEnd.plusDays(1);   // overnight shift

        if (start.isBefore(shiftEnd) && end.isAfter(shiftStart)) {
            throw new BusinessRuleException("OT_OVERLAPS_SHIFT",
                    "Overtime cannot overlap your regular shift (" + shift.getStartTime() + "–"
                    + shift.getEndTime() + "). Time worked beyond the shift is already counted "
                    + "automatically at check-out.");
        }
    }

    /**
     * The approver is always the logged-in user — never an id sent by the client — and cannot
     * act on their own request.
     */
    private Employee resolveReviewer(OvertimeRequest otRequest) {
        Long reviewerId = accessGuard.currentEmployeeId();
        if (reviewerId == null) {
            throw new BusinessRuleException("NO_EMPLOYEE_LINK",
                    "Your account is not linked to an employee record, so it cannot review requests.");
        }
        if (reviewerId.equals(otRequest.getEmployeeId())) {
            throw new BusinessRuleException("SELF_APPROVAL",
                    "You cannot approve or reject your own request.");
        }
        return findEmployee(reviewerId);
    }

    private void validatePendingStatus(OvertimeRequest otRequest) {
        if (otRequest.getStatus() != RegularizationStatus.PENDING) {
            throw new BusinessRuleException(
                    "Only PENDING overtime requests can be approved or rejected. "
                    + "Current status: " + otRequest.getStatus());
        }
    }

    private OvertimeResponse toResponse(
            OvertimeRequest r, Employee emp, Employee reviewer) {

        String reviewerName = reviewer != null
                ? reviewer.getFirstName() + " " + reviewer.getLastName() : null;

        String statusLabel = switch (r.getStatus()) {
            case PENDING   -> "Pending Approval";
            case APPROVED  -> "Approved";
            case REJECTED  -> "Rejected";
            case CANCELLED -> "Cancelled";
        };

        String otTypeLabel = switch (r.getOtType()) {
            case PRE_APPROVED -> "Pre-Approved";
            case POST_FACTO   -> "Post Facto";
            case WEEKEND      -> "Weekend";
            case HOLIDAY      -> "Holiday";
        };

        String durationFormatted = formatDuration(r.getDurationMinutes());

        return new OvertimeResponse(
                r.getOtId(),
                r.getEmployeeId(),
                r.getEmployeeCode(),
                emp.getFirstName() + " " + emp.getLastName(),
                r.getOtDate(),
                r.getOtDate().format(DATE_FMT),
                r.getOtType(),
                otTypeLabel,
                r.getStartTime(),
                r.getEndTime(),
                r.getDurationMinutes(),
                durationFormatted,
                r.getReason(),
                r.getProjectCode(),
                r.getStatus(),
                statusLabel,
                r.getRejectionReason(),
                r.getReviewedBy(),
                reviewerName,
                r.getReviewedAt(),
                r.getIsActive() == 1,
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }

    private String formatDuration(int totalMins) {
        int hours = totalMins / 60;
        int mins  = totalMins % 60;
        if (hours == 0) return mins + "m";
        if (mins  == 0) return hours + "h";
        return hours + "h " + mins + "m";
    }

    private OvertimeRequest findOtRequest(String otId) {
        return otRepo.findById(otId)
                .orElseThrow(() -> {
                    log.error("Overtime request not found — otId={}", otId);
                    return new ResourceNotFoundException(
                            "OvertimeRequest", "otId", otId);
                });
    }

    private Employee findEmployee(Long id) {
        return employeeRepo.findById(id)
                .orElseThrow(() -> {
                    log.error("Employee not found — id={}", id);
                    return new ResourceNotFoundException("Employee", "id", id);
                });
    }
}