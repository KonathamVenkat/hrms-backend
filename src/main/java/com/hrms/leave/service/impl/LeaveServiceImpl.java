package com.hrms.leave.service.impl;

import com.hrms.common.audit.CurrentAuditor;
import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.dto.PagedResponse;
import com.hrms.common.enums.Gender;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.leave.dto.request.*;
import com.hrms.leave.dto.response.*;
import com.hrms.leave.entity.*;
import com.hrms.leave.repository.*;
import com.hrms.leave.service.LeaveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveServiceImpl implements LeaveService {

    private final LeaveRequestRepository    leaveRequestRepository;
    private final LeaveBalanceRepository    leaveBalanceRepository;
    private final LeaveTypeRepository       leaveTypeRepository;
    private final EmployeeRepository        employeeRepository;
    private final HolidayCalendarRepository holidayCalendarRepository;
    private final EmployeeAccessGuard       employeeAccessGuard;
    private final LeaveAttachmentStorage    attachmentStorage;
    private final LeaveRequestAttachmentContentRepository attachmentContentRepository;

    // ══════════════════════════════════════════════════════════
    // APPLY LEAVE
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public LeaveRequestResponse applyLeave(Long employeeId,
                                            CreateLeaveRequest request,
                                            MultipartFile attachment) {
        log.info("Applying leave — emp={} type={} {} to {}",
            employeeId, request.getLeaveTypeCode(),
            request.getStartDate(), request.getEndDate());

        employeeAccessGuard.assertSelfOrPrivileged(employeeId);

        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Employee", "id", employeeId));

        LeaveType leaveType = leaveTypeRepository
            .findByCodeIgnoreCase(request.getLeaveTypeCode())
            .orElseThrow(() -> new BusinessRuleException(
                "INVALID_LEAVE_TYPE",
                "Leave type '" + request.getLeaveTypeCode() + "' not found."));

        if (leaveType.getIsActive() != 1) {
            throw new BusinessRuleException("LEAVE_TYPE_INACTIVE",
                "Leave type '" + leaveType.getNameEn() + "' is no longer active.");
        }

        if (!isGenderEligible(employee.getGender(), leaveType.getApplicableGender())) {
            throw new BusinessRuleException("GENDER_NOT_ELIGIBLE",
                leaveType.getNameEn() + " is only available to "
                + leaveType.getApplicableGender().toLowerCase() + " employees.");
        }

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessRuleException("INVALID_DATES",
                "End date cannot be before start date.");
        }
        if (request.getStartDate().isBefore(LocalDate.now())) {
            throw new BusinessRuleException("PAST_DATE",
                "Leave cannot be applied for past dates.");
        }

        int minNoticeDays = leaveType.getMinNoticeDays() != null ? leaveType.getMinNoticeDays() : 0;
        if (minNoticeDays > 0 && request.getStartDate().isBefore(LocalDate.now().plusDays(minNoticeDays))) {
            throw new BusinessRuleException("INSUFFICIENT_NOTICE",
                leaveType.getNameEn() + " requires at least " + minNoticeDays
                + " day(s) notice before the start date.");
        }

        double leaveDays = calculateWorkingDays(
            request.getStartDate(), request.getEndDate());
        if (leaveDays <= 0) {
            throw new BusinessRuleException("NO_WORKING_DAYS",
                "Selected date range contains no working days.");
        }

        Integer maxConsecutiveDays = leaveType.getMaxConsecutiveDays();
        if (maxConsecutiveDays != null && maxConsecutiveDays > 0 && leaveDays > maxConsecutiveDays) {
            throw new BusinessRuleException("EXCEEDS_MAX_CONSECUTIVE",
                leaveType.getNameEn() + " cannot be requested for more than "
                + maxConsecutiveDays + " consecutive day(s).");
        }

        List<LeaveRequest> overlapping = leaveRequestRepository.findOverlapping(
            employeeId, request.getStartDate(), request.getEndDate());
        if (!overlapping.isEmpty()) {
            throw new BusinessRuleException("DATE_OVERLAP",
                "You already have a leave request overlapping these dates.");
        }

        int year = request.getStartDate().getYear();
        LeaveBalance balance = leaveBalanceRepository
            .findByEmployeeIdAndLeaveTypeCodeAndYear(
                employeeId, request.getLeaveTypeCode(), year)
            .orElseThrow(() -> new BusinessRuleException("NO_BALANCE",
                "No " + leaveType.getNameEn() + " balance for " + year
                + ". Please contact HR."));

        if (balance.getAvailableDays() < leaveDays) {
            throw new BusinessRuleException("INSUFFICIENT_BALANCE",
                "Insufficient balance. Available: "
                + balance.getAvailableDays()
                + " days. Requested: " + leaveDays + " days.");
        }

        boolean hasFile = attachment != null && !attachment.isEmpty();
        if (leaveType.getRequiresDocument() != null && leaveType.getRequiresDocument() == 1 && !hasFile) {
            throw new BusinessRuleException("DOCUMENT_REQUIRED",
                leaveType.getNameEn() + " requires a supporting document. "
                + "Please attach one (PDF, JPG or PNG).");
        }

        // Validated after every business-rule check; the bytes are saved with the request below.
        LeaveAttachmentStorage.Prepared prepared = hasFile
            ? attachmentStorage.prepare(attachment, leaveType) : null;

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setEmployeeId(employeeId);
        leaveRequest.setEmployeeCode(employee.getEmployeeCode());
        leaveRequest.setLeaveTypeCode(request.getLeaveTypeCode());
        leaveRequest.setStartDate(request.getStartDate());
        leaveRequest.setEndDate(request.getEndDate());
        leaveRequest.setTotalDays(leaveDays);
        leaveRequest.setReason(request.getReason());
        if (prepared != null) {
            leaveRequest.setAttachmentName(prepared.originalName());
            leaveRequest.setAttachmentSize(prepared.sizeBytes());
        }
        leaveRequest.setStatus(LeaveStatus.PENDING);
        leaveRequest.setIsActive(true);
        leaveRequest.setCreatedBy(CurrentAuditor.name());
        leaveRequest.setCreatedAt(LocalDateTime.now());
        leaveRequest.setUpdatedBy(CurrentAuditor.name());
        leaveRequest.setUpdatedAt(LocalDateTime.now());

        // Request, attachment bytes and balance change commit or roll back together.
        LeaveRequest saved = leaveRequestRepository.saveAndFlush(leaveRequest);
        if (prepared != null) {
            attachmentContentRepository.save(
                new LeaveRequestAttachmentContent(saved.getLeaveReqId(), prepared.bytes()));
        }

        balance.setPendingDays(balance.getPendingDays() + leaveDays);
        balance.setUpdatedAt(LocalDateTime.now());
        leaveBalanceRepository.save(balance);

        log.info("Leave applied. ID={} days={}", saved.getLeaveReqId(), leaveDays);
        return toResponse(saved, employee, leaveType, balance);
    }

    // ══════════════════════════════════════════════════════════
    // GET LEAVES BY EMPLOYEE
    // ══════════════════════════════════════════════════════════

    @Override
    public PagedResponse<LeaveRequestResponse> getLeavesByEmployee(
            Long employeeId, LeaveFilterRequest filter) {

        employeeAccessGuard.assertSelfOrPrivileged(employeeId);

        if (!employeeRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException("Employee", "id", employeeId);
        }

        Pageable pageable = buildPageable(filter);
        Page<LeaveRequest> page;

        if (filter.getYear() != null) {
            Pageable nativePageable = PageRequest.of(
                filter.getPage(), filter.getSize());
            page = leaveRequestRepository
                .findByEmployeeIdAndYear(
                    employeeId, filter.getYear(), nativePageable);

        } else if (filter.getStatus() != null
                && !filter.getStatus().isBlank()) {
            page = leaveRequestRepository
                .findByEmployeeIdAndStatusOrderByCreatedAtDesc(
                    employeeId,
                    LeaveStatus.valueOf(filter.getStatus()),
                    pageable);

        } else if (filter.getLeaveTypeCode() != null
                && !filter.getLeaveTypeCode().isBlank()) {
            page = leaveRequestRepository
                .findByEmployeeIdAndLeaveTypeCodeOrderByCreatedAtDesc(
                    employeeId, filter.getLeaveTypeCode(), pageable);

        } else {
            page = leaveRequestRepository
                .findByEmployeeIdOrderByCreatedAtDesc(employeeId, pageable);
        }

        return toPagedResponse(page);
    }

    // ══════════════════════════════════════════════════════════
    // DOWNLOAD ATTACHMENT
    // ══════════════════════════════════════════════════════════

    @Override
    public LeaveAttachmentDownload getAttachment(Long employeeId, Long leaveReqId) {
        employeeAccessGuard.assertSelfOrPrivileged(employeeId);

        LeaveRequest lr = leaveRequestRepository.findById(leaveReqId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "LeaveRequest", "id", leaveReqId));

        if (!lr.getEmployeeId().equals(employeeId) || lr.getAttachmentName() == null) {
            throw new ResourceNotFoundException("LeaveAttachment", "leaveReqId", leaveReqId);
        }

        LeaveRequestAttachmentContent stored = attachmentContentRepository.findById(leaveReqId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "LeaveAttachment", "leaveReqId", leaveReqId));
        return new LeaveAttachmentDownload(
            new ByteArrayResource(stored.getContent()), lr.getAttachmentName());
    }

    // ══════════════════════════════════════════════════════════
    // GET SINGLE LEAVE
    // ══════════════════════════════════════════════════════════

    @Override
    public LeaveRequestResponse getLeaveById(Long employeeId, Long leaveReqId) {
        employeeAccessGuard.assertSelfOrPrivileged(employeeId);

        LeaveRequest lr = leaveRequestRepository.findById(leaveReqId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "LeaveRequest", "id", leaveReqId));

        if (!lr.getEmployeeId().equals(employeeId)) {
            throw new ResourceNotFoundException("LeaveRequest", "id", leaveReqId);
        }

        return toResponse(lr, null, null, null);
    }

    // ══════════════════════════════════════════════════════════
    // CANCEL LEAVE
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public void cancelLeave(Long leaveReqId, Long employeeId) {
        log.info("Cancelling leave — leaveReqId={} employeeId={}",
            leaveReqId, employeeId);

        employeeAccessGuard.assertSelfOrPrivileged(employeeId);

        LeaveRequest lr = leaveRequestRepository.findById(leaveReqId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "LeaveRequest", "id", leaveReqId));

        if (!lr.getEmployeeId().equals(employeeId)) {
            throw new BusinessRuleException("UNAUTHORIZED",
                "You can only cancel your own leave requests.");
        }

        if (LeaveStatus.PENDING != lr.getStatus()) {
            throw new BusinessRuleException("INVALID_STATUS",
                "Only PENDING leave requests can be cancelled. "
                + "Current status: " + lr.getStatus().name());
        }

        int year = lr.getStartDate().getYear();
        leaveBalanceRepository
            .findByEmployeeIdAndLeaveTypeCodeAndYear(
                lr.getEmployeeId(), lr.getLeaveTypeCode(), year)
            .ifPresent(balance -> {
                balance.setPendingDays(
                    Math.max(0, balance.getPendingDays() - lr.getTotalDays()));
                balance.setUpdatedAt(LocalDateTime.now());
                leaveBalanceRepository.save(balance);
            });

        lr.setStatus(LeaveStatus.CANCELLED);
        lr.setUpdatedBy(CurrentAuditor.name());
        lr.setUpdatedAt(LocalDateTime.now());
        leaveRequestRepository.save(lr);
    }

    // ══════════════════════════════════════════════════════════
    // GET BALANCES
    // ══════════════════════════════════════════════════════════

    @Override
    public List<LeaveBalanceResponse> getBalances(Long employeeId, Integer year) {
        employeeAccessGuard.assertSelfOrPrivileged(employeeId);

        int targetYear = (year != null) ? year : LocalDate.now().getYear();
        return leaveBalanceRepository
            .findByEmployeeIdAndYear(employeeId, targetYear)
            .stream().map(this::toBalanceResponse)
            .collect(Collectors.toList());
    }

    // ══════════════════════════════════════════════════════════
    // GET ALL LEAVES (HR view) — with employeeId filter
    // ══════════════════════════════════════════════════════════

    @Override
    public PagedResponse<LeaveRequestResponse> getAllLeaves(LeaveFilterRequest filter) {
        Pageable pageable = buildPageable(filter);
        Page<LeaveRequest> page;

        // Filter by specific employee
        if (filter.getEmployeeId() != null) {
            Pageable nativePaging = PageRequest.of(filter.getPage(), filter.getSize());
            if (filter.getStatus() != null && !filter.getStatus().isBlank()) {
                page = leaveRequestRepository
                    .findByEmployeeIdAndStatusOrderByCreatedAtDesc(
                        filter.getEmployeeId(),
                        LeaveStatus.valueOf(filter.getStatus()),
                        nativePaging);
            } else {
                page = leaveRequestRepository
                    .findByEmployeeIdOrderByCreatedAtDesc(
                        filter.getEmployeeId(), nativePaging);
            }
        } else if (filter.getStatus() != null && !filter.getStatus().isBlank()) {
            page = leaveRequestRepository.findByStatusOrderByCreatedAtDesc(
                LeaveStatus.valueOf(filter.getStatus()), pageable);
        } else {
            page = leaveRequestRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        log.info("getAllLeaves — found {} records", page.getTotalElements());
        return toPagedResponse(page);
    }

    // ══════════════════════════════════════════════════════════
    // PROCESS LEAVE — Approve / Reject
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public LeaveRequestResponse processLeave(Long leaveReqId,
                                              ApproveLeaveRequest request,
                                              String approver) {
        log.info("Processing leave — id={} action={} approver={}",
            leaveReqId, request.getAction(), approver);

        LeaveRequest lr = leaveRequestRepository.findById(leaveReqId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "LeaveRequest", "id", leaveReqId));

        if (LeaveStatus.PENDING != lr.getStatus()) {
            throw new BusinessRuleException("INVALID_STATUS",
                "Only PENDING requests can be processed. "
                + "Status: " + lr.getStatus().name());
        }

        int year = lr.getStartDate().getYear();
        LeaveBalance balance = leaveBalanceRepository
            .findByEmployeeIdAndLeaveTypeCodeAndYear(
                lr.getEmployeeId(), lr.getLeaveTypeCode(), year)
            .orElseThrow(() -> new ResourceNotFoundException(
                "LeaveBalance", "employeeId", lr.getEmployeeId()));

        if ("APPROVED".equals(request.getAction())) {
            balance.setPendingDays(
                Math.max(0, balance.getPendingDays() - lr.getTotalDays()));
            balance.setUsedDays(balance.getUsedDays() + lr.getTotalDays());
            lr.setStatus(LeaveStatus.APPROVED);
        } else {
            balance.setPendingDays(
                Math.max(0, balance.getPendingDays() - lr.getTotalDays()));
            lr.setStatus(LeaveStatus.REJECTED);
            lr.setRejectionReason(request.getRemarks());
        }

        balance.setUpdatedAt(LocalDateTime.now());
        leaveBalanceRepository.save(balance);

        lr.setApprovedBy(approver);
        lr.setApprovedAt(LocalDateTime.now());
        lr.setUpdatedBy(approver);
        lr.setUpdatedAt(LocalDateTime.now());

        return toResponse(leaveRequestRepository.save(lr), null, null, balance);
    }

    // ══════════════════════════════════════════════════════════
    // PENDING COUNT
    // ══════════════════════════════════════════════════════════

    @Override
    public Long getPendingCount() {
        return leaveRequestRepository.countByStatus(LeaveStatus.PENDING);
    }

    // ── Private helpers ───────────────────────────────────────

    /**
     * Counts working days between two dates (inclusive), excluding the
     * Saturday/Sunday weekend (see the matching definition in
     * LeaveCalendarServiceImpl) and any active
     * public holiday falling on what would otherwise be a working day.
     */
    private double calculateWorkingDays(LocalDate start, LocalDate end) {
        Set<LocalDate> holidayDates = holidayCalendarRepository
            .findHolidaysBetween(start, end)
            .stream()
            .map(HolidayCalendar::getHolidayDate)
            .collect(Collectors.toSet());

        double days = 0;
        LocalDate current = start;
        while (!current.isAfter(end)) {
            DayOfWeek dow = current.getDayOfWeek();
            boolean isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
            if (!isWeekend && !holidayDates.contains(current)) {
                days++;
            }
            current = current.plusDays(1);
        }
        return days;
    }

    private boolean isGenderEligible(Gender employeeGender, String applicableGender) {
        if (applicableGender == null || "ALL".equalsIgnoreCase(applicableGender)) {
            return true;
        }
        return employeeGender != null && employeeGender.name().equalsIgnoreCase(applicableGender);
    }

    private Pageable buildPageable(LeaveFilterRequest filter) {
        Sort sort = "asc".equalsIgnoreCase(filter.getSortDir())
            ? Sort.by(filter.getSortBy()).ascending()
            : Sort.by(filter.getSortBy()).descending();
        return PageRequest.of(filter.getPage(), filter.getSize(), sort);
    }

    private PagedResponse<LeaveRequestResponse> toPagedResponse(Page<LeaveRequest> page) {
        // Map entity page → DTO page, then use from() static factory
        Page<LeaveRequestResponse> mappedPage = page.map(
            lr -> toResponse(lr, null, null, null));

        return PagedResponse.from(mappedPage);
    }

    private LeaveRequestResponse toResponse(LeaveRequest lr,
                                             Employee employee,
                                             LeaveType leaveType,
                                             LeaveBalance balance) {
        String  leaveTypeName = lr.getLeaveTypeCode();
        Boolean isPaid        = null;
        try {
            Optional<LeaveType> lt = leaveTypeRepository
                .findByCodeIgnoreCase(lr.getLeaveTypeCode());
            if (lt.isPresent()) {
                leaveTypeName = lt.get().getNameEn();
                isPaid        = lt.get().getIsPaid() == 1;
            }
        } catch (Exception ignored) {}

        String employeeName = null;
        String employeeCode = lr.getEmployeeCode();
        try {
            Employee emp = (employee != null) ? employee
                : employeeRepository.findById(lr.getEmployeeId()).orElse(null);
            if (emp != null) {
                employeeName = emp.getFirstName() + " " + emp.getLastName();
                employeeCode = emp.getEmployeeCode();
            }
        } catch (Exception ignored) {}

        return LeaveRequestResponse.builder()
            .leaveReqId(lr.getLeaveReqId())
            .employeeId(lr.getEmployeeId())
            .employeeCode(employeeCode)
            .employeeName(employeeName)
            .leaveTypeCode(lr.getLeaveTypeCode())
            .leaveTypeName(leaveTypeName)
            .isPaid(isPaid)
            .startDate(lr.getStartDate() != null ? lr.getStartDate().toString() : null)
            .endDate(lr.getEndDate() != null ? lr.getEndDate().toString() : null)
            .totalDays(lr.getTotalDays())
            .reason(lr.getReason())
            .status(lr.getStatus() != null ? lr.getStatus().name() : null)
            .approvedBy(lr.getApprovedBy())
            .approvedAt(lr.getApprovedAt() != null ? lr.getApprovedAt().toString() : null)
            .remarks(lr.getRejectionReason())
            .hasAttachment(lr.getAttachmentName() != null)
            .attachmentName(lr.getAttachmentName())
            .attachmentSize(lr.getAttachmentSize())
            .balanceAvailable(balance != null ? balance.getAvailableDays() : null)
            .balanceTotal(balance != null ? balance.getTotalDays() : null)
            .createdAt(lr.getCreatedAt() != null ? lr.getCreatedAt().toString() : null)
            .updatedAt(lr.getUpdatedAt() != null ? lr.getUpdatedAt().toString() : null)
            .build();
    }

    private LeaveBalanceResponse toBalanceResponse(LeaveBalance lb) {
        String leaveTypeName = lb.getLeaveTypeCode();
        try {
            Optional<LeaveType> lt = leaveTypeRepository
                .findByCodeIgnoreCase(lb.getLeaveTypeCode());
            if (lt.isPresent()) leaveTypeName = lt.get().getNameEn();
        } catch (Exception ignored) {}

        return LeaveBalanceResponse.builder()
            .balanceId(lb.getBalanceId())
            .employeeId(lb.getEmployeeId())
            .leaveType(lb.getLeaveTypeCode())
            .leaveTypeName(leaveTypeName)
            .year(lb.getYear())
            .totalDays(lb.getTotalDays())
            .usedDays(lb.getUsedDays())
            .pendingDays(lb.getPendingDays())
            .availableDays(lb.getAvailableDays())
            .build();
    }
}
