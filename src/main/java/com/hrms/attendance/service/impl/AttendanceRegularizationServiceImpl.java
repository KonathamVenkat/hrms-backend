package com.hrms.attendance.service.impl;

import com.hrms.attendance.dto.request.RegularizationActionRequest;
import com.hrms.attendance.dto.request.RegularizationRequest;
import com.hrms.attendance.dto.response.RegularizationResponse;
import com.hrms.attendance.entity.AttendanceLog;
import com.hrms.attendance.entity.AttendanceRegularization;
import com.hrms.attendance.enums.AttendanceStatus;
import com.hrms.attendance.enums.RegularizationStatus;
import com.hrms.attendance.repository.AttendanceLogRepository;
import com.hrms.attendance.repository.AttendanceRegularizationRepository;
import com.hrms.attendance.service.AttendanceRegularizationService;
import com.hrms.attendance.service.AttendanceSummaryService;
import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.employee.entity.WorkShift;
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
public class AttendanceRegularizationServiceImpl
        implements AttendanceRegularizationService {

    private final AttendanceRegularizationRepository regRepo;
    private final AttendanceLogRepository            logRepo;
    private final EmployeeRepository                 employeeRepo;
    private final EmployeeAccessGuard                accessGuard;
    private final AttendanceCalculator               calculator;
    private final AttendanceDayClassifier            classifier;
    private final AttendanceSummaryService           summaryService;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("EEE, MMM d yyyy");

    // ────────────────────────────────────────────────────────
    // SUBMIT
    // ────────────────────────────────────────────────────────
    @Override
    @Transactional
    public RegularizationResponse submit(RegularizationRequest request) {
        log.info("Regularization submit — employeeId={}, date={}",
                request.employeeId(), request.attendanceDate());

        accessGuard.assertSelfOrPrivileged(request.employeeId());

        Employee employee = findEmployee(request.employeeId());
        LocalDate attendanceDate = LocalDate.parse(request.attendanceDate());

        // ── Business rules ────────────────────────────────────
        // 1. Cannot regularize future dates
        if (attendanceDate.isAfter(LocalDate.now())) {
            throw new BusinessRuleException(
                    "Regularization cannot be submitted for a future date.");
        }

        // 2. Cannot regularize more than 30 days in the past
        if (attendanceDate.isBefore(LocalDate.now().minusDays(30))) {
            throw new BusinessRuleException(
                    "Regularization can only be submitted for dates within the last 30 days.");
        }

        // 3. Check for duplicate pending request for same date
        boolean hasPending = regRepo
                .existsByEmployeeIdAndAttendanceDateAndStatusAndIsActive(
                        employee.getId(), attendanceDate,
                        RegularizationStatus.PENDING, 1);
        if (hasPending) {
            log.warn("Duplicate regularization attempt — employeeId={}, date={}",
                    employee.getId(), attendanceDate);
            throw new BusinessRuleException(
                    "A pending regularization request already exists for " + attendanceDate);
        }

        // ── Find existing attendance log for that date ────────
        Optional<AttendanceLog> existingLog = logRepo
                .findByEmployeeIdAndAttendanceDateAndIsActive(
                        employee.getId(), attendanceDate, 1);

        Long logId = existingLog.map(AttendanceLog::getLogId).orElse(null);
        log.debug("Existing log found: {}", logId != null ? "yes (logId=" + logId + ")" : "no");

        // ── Parse requested times ─────────────────────────────
        LocalDateTime requestedIn  = LocalDateTime.parse(request.requestedInTime());
        LocalDateTime requestedOut = request.requestedOutTime() != null
                ? LocalDateTime.parse(request.requestedOutTime()) : null;

        // 4. Validate time order
        if (requestedOut != null && !requestedOut.isAfter(requestedIn)) {
            throw new BusinessRuleException(
                    "Requested check-out time must be after check-in time.");
        }

        // 5. Requested times must belong to the date being regularized (check-out may fall
        //    on the next day for an overnight shift), and cannot be in the future.
        if (!requestedIn.toLocalDate().equals(attendanceDate)) {
            throw new BusinessRuleException(
                    "Requested check-in time must be on the attendance date " + attendanceDate + ".");
        }
        if (requestedOut != null && !requestedOut.toLocalDate().equals(attendanceDate)
                && !requestedOut.toLocalDate().equals(attendanceDate.plusDays(1))) {
            throw new BusinessRuleException(
                    "Requested check-out time must be on " + attendanceDate
                    + " or the following day.");
        }
        LocalDateTime nowTs = LocalDateTime.now();
        if (requestedIn.isAfter(nowTs) || (requestedOut != null && requestedOut.isAfter(nowTs))) {
            throw new BusinessRuleException("Requested times cannot be in the future.");
        }

        // 6. A day of approved leave is not an attendance to correct; a weekend or holiday only is
        //    when overtime was approved for it (the same condition as checking in).
        existingLog.ifPresent(l -> assertRegularizable(l));
        classifier.assertMayRegularize(employee.getId(), calculator.resolveShift(employee.getId()), attendanceDate);

        // ── Persist ───────────────────────────────────────────
        Long regId = regRepo.findNextSequenceValue();

        AttendanceRegularization reg = AttendanceRegularization.builder()
                .regId(regId)
                .employeeId(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .attendanceDate(attendanceDate)
                .logId(logId)
                .requestedInTime(requestedIn)
                .requestedOutTime(requestedOut)
                .reason(request.reason())
                .status(RegularizationStatus.PENDING)
                .build();

        reg.setCreatedBy(employee.getEmployeeCode());
        reg.setCreatedAt(LocalDateTime.now());
        reg.setUpdatedAt(LocalDateTime.now());

        AttendanceRegularization saved = regRepo.save(reg);
        log.info("Regularization submitted successfully — regId={}, employeeId={}",
                saved.getRegId(), employee.getId());

        return toResponse(saved, employee, null);
    }

    // ────────────────────────────────────────────────────────
    // CANCEL (Employee cancels own pending request)
    // ────────────────────────────────────────────────────────
    @Override
    @Transactional
    public RegularizationResponse cancel(Long regId, Long employeeId) {
        log.info("Regularization cancel — regId={}, employeeId={}", regId, employeeId);

        accessGuard.assertSelfOrPrivileged(employeeId);

        AttendanceRegularization reg = findRegularization(regId);

        // Only the employee who submitted can cancel
        if (!reg.getEmployeeId().equals(employeeId)) {
            throw new BusinessRuleException(
                    "You can only cancel your own regularization requests.");
        }

        // Can only cancel PENDING requests
        if (reg.getStatus() != RegularizationStatus.PENDING) {
            throw new BusinessRuleException(
                    "Only PENDING requests can be cancelled. Current status: "
                            + reg.getStatus());
        }

        reg.setStatus(RegularizationStatus.CANCELLED);
        reg.setUpdatedAt(LocalDateTime.now());

        AttendanceRegularization saved = regRepo.save(reg);
        log.info("Regularization cancelled — regId={}", regId);

        Employee employee = findEmployee(employeeId);
        return toResponse(saved, employee, null);
    }

    // ────────────────────────────────────────────────────────
    // APPROVE
    // ────────────────────────────────────────────────────────
    @Override
    @Transactional
    public RegularizationResponse approve(Long regId, RegularizationActionRequest request) {
        log.info("Regularization approve — regId={}", regId);

        AttendanceRegularization reg = findRegularization(regId);
        validatePendingStatus(reg);

        Employee employee = findEmployee(reg.getEmployeeId());
        Employee reviewer = resolveReviewer(reg);

        // ── Update regularization status ──────────────────────
        reg.setStatus(RegularizationStatus.APPROVED);
        reg.setReviewedBy(reviewer.getId());
        reg.setReviewedAt(LocalDateTime.now());
        reg.setUpdatedBy(reviewer.getEmployeeCode());
        reg.setUpdatedAt(LocalDateTime.now());

        // ── Correct the attendance log ────────────────────────
        correctAttendanceLog(reg, employee);

        AttendanceRegularization saved = regRepo.save(reg);
        log.info("Regularization approved — regId={}, employeeId={}, date={}",
                regId, employee.getId(), reg.getAttendanceDate());

        return toResponse(saved, employee, reviewer);
    }

    // ────────────────────────────────────────────────────────
    // REJECT
    // ────────────────────────────────────────────────────────
    @Override
    @Transactional
    public RegularizationResponse reject(Long regId, RegularizationActionRequest request) {
        log.info("Regularization reject — regId={}", regId);

        if (request.rejectionReason() == null || request.rejectionReason().isBlank()) {
            throw new BusinessRuleException(
                    "Rejection reason is required when rejecting a regularization.");
        }

        AttendanceRegularization reg = findRegularization(regId);
        validatePendingStatus(reg);

        Employee reviewer = resolveReviewer(reg);

        reg.setStatus(RegularizationStatus.REJECTED);
        reg.setRejectionReason(request.rejectionReason());
        reg.setReviewedBy(reviewer.getId());
        reg.setReviewedAt(LocalDateTime.now());
        reg.setUpdatedBy(reviewer.getEmployeeCode());
        reg.setUpdatedAt(LocalDateTime.now());

        AttendanceRegularization saved = regRepo.save(reg);
        log.info("Regularization rejected — regId={}, reason={}",
                regId, request.rejectionReason());

        Employee employee = findEmployee(reg.getEmployeeId());
        return toResponse(saved, employee, reviewer);
    }

    // ────────────────────────────────────────────────────────
    // READ OPERATIONS
    // ────────────────────────────────────────────────────────
    @Override
    public PagedResponse<RegularizationResponse> getMyRequests(
            Long employeeId, Pageable pageable) {
        log.info("Fetching regularization requests — employeeId={}, page={}",
                employeeId, pageable.getPageNumber());

        accessGuard.assertSelfOrPrivileged(employeeId);
        Employee employee = findEmployee(employeeId);
        Page<AttendanceRegularization> page = regRepo
                .findByEmployeeIdAndIsActiveOrderByCreatedAtDesc(
                        employeeId, 1, pageable);

        log.info("Found {} regularization requests for employeeId={}",
                page.getTotalElements(), employeeId);

        return PagedResponse.from(page.map(r -> toResponse(r, employee, null)));
    }

    @Override
    public RegularizationResponse getById(Long regId) {
        log.info("Fetching regularization by id={}", regId);
        AttendanceRegularization reg = findRegularization(regId);
        accessGuard.assertSelfOrPrivileged(reg.getEmployeeId());
        Employee employee = findEmployee(reg.getEmployeeId());
        Employee reviewer = reg.getReviewedBy() != null
                ? findEmployee(reg.getReviewedBy()) : null;
        return toResponse(reg, employee, reviewer);
    }

    @Override
    public PagedResponse<RegularizationResponse> getAllRequests(
            RegularizationStatus status,
            Long employeeId,
            LocalDate from,
            LocalDate to,
            Pageable pageable) {

        log.info("Fetching all regularizations — status={}, employeeId={}, from={}, to={}",
                status, employeeId, from, to);

        Page<AttendanceRegularization> page =
                regRepo.findByFilters(status, employeeId, from, to, pageable);

        log.info("Found {} total regularization requests", page.getTotalElements());

        return PagedResponse.from(page.map(r -> {
            Employee emp      = findEmployee(r.getEmployeeId());
            Employee reviewer = r.getReviewedBy() != null
                    ? findEmployee(r.getReviewedBy()) : null;
            return toResponse(r, emp, reviewer);
        }));
    }

    @Override
    public long getPendingCount() {
        long count = regRepo.countByStatusAndIsActive(
                RegularizationStatus.PENDING, 1);
        log.debug("Pending regularization count: {}", count);
        return count;
    }

    // ── Private helpers ───────────────────────────────────────

    /**
     * When a regularization is APPROVED, correct or create the attendance log. The corrected
     * times are scored by the same {@link AttendanceCalculator} as a live punch, so late /
     * working / overtime / early-leave minutes and the status match what a real punch at
     * those times would have produced (not a blanket PRESENT with stale minutes).
     */
    private void correctAttendanceLog(
            AttendanceRegularization reg, Employee employee) {

        log.debug("Correcting attendance log for employeeId={}, date={}",
                employee.getId(), reg.getAttendanceDate());

        Optional<AttendanceLog> existingLog = reg.getLogId() != null
                ? logRepo.findById(reg.getLogId())
                : logRepo.findByEmployeeIdAndAttendanceDateAndIsActive(
                        employee.getId(), reg.getAttendanceDate(), 1);

        // Renamed to 'attendanceLog' to avoid shadowing the Slf4j 'log' logger
        AttendanceLog attendanceLog;
        boolean isNew = existingLog.isEmpty();
        if (isNew) {
            attendanceLog = AttendanceLog.builder()
                    .logId(logRepo.findNextSequenceValue())
                    .employeeId(employee.getId())
                    .employeeCode(employee.getEmployeeCode())
                    .attendanceDate(reg.getAttendanceDate())
                    .build();
            attendanceLog.setCreatedBy(employee.getEmployeeCode());
            attendanceLog.setCreatedAt(LocalDateTime.now());
        } else {
            attendanceLog = existingLog.get();
            // The day may have been marked leave/holiday since the request was submitted
            assertRegularizable(attendanceLog);
        }

        LocalDateTime in  = reg.getRequestedInTime();
        LocalDateTime out = reg.getRequestedOutTime() != null
                ? reg.getRequestedOutTime() : attendanceLog.getCheckOutTime();
        if (out != null && !out.isAfter(in)) {
            throw new BusinessRuleException(
                    "The existing check-out time is not after the requested check-in time. "
                    + "The employee must request a check-out time too.");
        }

        WorkShift shift = calculator.resolveShift(employee.getId());
        classifier.assertMayRegularize(employee.getId(), shift, reg.getAttendanceDate());
        boolean nonWorkingDay = classifier.isNonWorkingDay(shift, reg.getAttendanceDate());
        calculator.applyCheckIn(attendanceLog, in, shift, nonWorkingDay);
        if (out != null) {
            calculator.applyCheckOut(attendanceLog, out, shift, nonWorkingDay);
        }
        attendanceLog.setIsRegularized(1);
        attendanceLog.setUpdatedAt(LocalDateTime.now());

        AttendanceLog saved = logRepo.save(attendanceLog);
        reg.setLogId(saved.getLogId());
        log.info("Attendance log {} via regularization — logId={}",
                isNew ? "created" : "updated", saved.getLogId());

        // Past days are already baked into the stored monthly summary — refresh it.
        if (reg.getAttendanceDate().isBefore(LocalDate.now())) {
            summaryService.recalculateSummary(employee.getId(),
                    reg.getAttendanceDate().getYear(), reg.getAttendanceDate().getMonthValue());
        }
    }

    /** A leave day is system-determined and can't be overridden by a punch correction. */
    private void assertRegularizable(AttendanceLog l) {
        if (l.getStatus() == AttendanceStatus.ON_LEAVE) {
            throw new BusinessRuleException(
                    "This day is marked " + l.getStatus() + " and cannot be regularized.");
        }
    }

    /**
     * The approver is always the logged-in user — never an id sent by the client — and cannot
     * act on their own request.
     */
    private Employee resolveReviewer(AttendanceRegularization reg) {
        Long reviewerId = accessGuard.currentEmployeeId();
        if (reviewerId == null) {
            throw new BusinessRuleException("NO_EMPLOYEE_LINK",
                    "Your account is not linked to an employee record, so it cannot review requests.");
        }
        if (reviewerId.equals(reg.getEmployeeId())) {
            throw new BusinessRuleException("SELF_APPROVAL",
                    "You cannot approve or reject your own request.");
        }
        return findEmployee(reviewerId);
    }

    private void validatePendingStatus(AttendanceRegularization reg) {
        if (reg.getStatus() != RegularizationStatus.PENDING) {
            throw new BusinessRuleException(
                    "Only PENDING regularizations can be approved or rejected. " +
                    "Current status: " + reg.getStatus());
        }
    }

    private RegularizationResponse toResponse(
            AttendanceRegularization reg,
            Employee employee,
            Employee reviewer) {

        String reviewerName = reviewer != null
                ? reviewer.getFirstName() + " " + reviewer.getLastName() : null;

        String statusLabel = switch (reg.getStatus()) {
            case PENDING   -> "Pending Approval";
            case APPROVED  -> "Approved";
            case REJECTED  -> "Rejected";
            case CANCELLED -> "Cancelled";
        };

        return new RegularizationResponse(
                reg.getRegId(),
                reg.getEmployeeId(),
                reg.getEmployeeCode(),
                employee.getFirstName() + " " + employee.getLastName(),
                reg.getAttendanceDate(),
                reg.getAttendanceDate().format(DATE_FMT),
                reg.getLogId(),
                reg.getRequestedInTime(),
                reg.getRequestedOutTime(),
                reg.getReason(),
                reg.getStatus(),
                statusLabel,
                reg.getRejectionReason(),
                reg.getReviewedBy(),
                reviewerName,
                reg.getReviewedAt(),
                reg.getIsActive() == 1,
                reg.getCreatedAt(),
                reg.getUpdatedAt()
        );
    }

    private AttendanceRegularization findRegularization(Long regId) {
        return regRepo.findById(regId)
                .orElseThrow(() -> {
                    log.error("Regularization not found — regId={}", regId);
                    return new ResourceNotFoundException(
                            "AttendanceRegularization", "regId", regId);
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