package com.hrms.attendance.service.impl;

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

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("EEE, MMM d yyyy");

    // ────────────────────────────────────────────────────
    // SUBMIT
    // ────────────────────────────────────────────────────
    @Override
    @Transactional
    public OvertimeResponse submit(OvertimeSubmitRequest request) {
        log.info("Overtime submit — employeeId={}, date={}, type={}",
                request.employeeId(), request.otDate(), request.otType());

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
        log.info("Overtime approve — otId={}, reviewedBy={}", otId, request.reviewedBy());

        OvertimeRequest otRequest = findOtRequest(otId);
        validatePendingStatus(otRequest);

        Employee employee = findEmployee(otRequest.getEmployeeId());
        Employee reviewer = findEmployee(request.reviewedBy());

        otRequest.setStatus(RegularizationStatus.APPROVED);
        otRequest.setReviewedBy(reviewer.getId());
        otRequest.setReviewedAt(LocalDateTime.now());
        otRequest.setUpdatedBy(reviewer.getEmployeeCode());
        otRequest.setUpdatedAt(LocalDateTime.now());

        // ── Update attendance log overtime minutes ────────
        updateAttendanceLogOvertime(otRequest, employee);

        OvertimeRequest saved = otRepo.save(otRequest);
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
        log.info("Overtime reject — otId={}, reviewedBy={}", otId, request.reviewedBy());

        if (request.rejectionReason() == null || request.rejectionReason().isBlank()) {
            throw new BusinessRuleException(
                    "Rejection reason is required when rejecting an overtime request.");
        }

        OvertimeRequest otRequest = findOtRequest(otId);
        validatePendingStatus(otRequest);

        Employee reviewer = findEmployee(request.reviewedBy());

        otRequest.setStatus(RegularizationStatus.REJECTED);
        otRequest.setRejectionReason(request.rejectionReason());
        otRequest.setReviewedBy(reviewer.getId());
        otRequest.setReviewedAt(LocalDateTime.now());
        otRequest.setUpdatedBy(reviewer.getEmployeeCode());
        otRequest.setUpdatedAt(LocalDateTime.now());

        OvertimeRequest saved = otRepo.save(otRequest);
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
     * On approval, find the attendance log for the OT date
     * and update overtime_minutes.
     * If no log exists, log a warning — HR should ensure
     * the employee has an attendance log for that date.
     */
    private void updateAttendanceLogOvertime(
            OvertimeRequest otRequest, Employee employee) {

        log.debug("Updating attendance log OT minutes — employeeId={}, date={}",
                employee.getId(), otRequest.getOtDate());

        Optional<AttendanceLog> logOpt = logRepo
                .findByEmployeeIdAndAttendanceDateAndIsActive(
                        employee.getId(), otRequest.getOtDate(), 1);

        if (logOpt.isPresent()) {
            AttendanceLog attendanceLog = logOpt.get();
            int currentOt = attendanceLog.getOvertimeMinutes() != null
                    ? attendanceLog.getOvertimeMinutes() : 0;
            attendanceLog.setOvertimeMinutes(
                    currentOt + otRequest.getDurationMinutes());
            attendanceLog.setUpdatedAt(LocalDateTime.now());
            logRepo.save(attendanceLog);
            log.info("Attendance log OT updated — logId={}, totalOtMins={}",
                    attendanceLog.getLogId(),
                    currentOt + otRequest.getDurationMinutes());
        } else {
            log.warn("No attendance log found for employeeId={} on OT date={}. " +
                     "OT minutes not applied to log — please ensure employee has punched in.",
                    employee.getId(), otRequest.getOtDate());
        }
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