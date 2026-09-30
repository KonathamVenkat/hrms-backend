package com.hrms.leave.service.impl;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.config.UploadLimits;
import com.hrms.leave.dto.request.AdjustBalanceRequest;
import com.hrms.leave.dto.request.InitializeBalancesRequest;
import com.hrms.leave.dto.response.InitializationResultResponse;
import com.hrms.leave.dto.response.LeaveBalanceResponse;
import com.hrms.leave.entity.LeaveBalance;
import com.hrms.leave.entity.LeaveType;
import com.hrms.leave.repository.LeaveBalanceRepository;
import com.hrms.leave.repository.LeaveTypeRepository;
import com.hrms.leave.service.LeaveBalanceService;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.entity.Employee;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveBalanceServiceImpl implements LeaveBalanceService {

    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveTypeRepository    leaveTypeRepository;
    private final EmployeeRepository     employeeRepository;
    private final EmployeeAccessGuard    employeeAccessGuard;
    private final UploadLimits           uploadLimits;

    // ── Get balances ──────────────────────────────────────────

    @Override
    public List<LeaveBalanceResponse> getEmployeeBalances(Long employeeId, Integer year) {
        employeeAccessGuard.assertSelfOrPrivileged(employeeId);

        if (!employeeRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException("Employee", "id", employeeId);
        }
        int targetYear = (year != null) ? year : LocalDateTime.now().getYear();
        return leaveBalanceRepository
            .findByEmployeeIdAndYear(employeeId, targetYear)
            .stream()
            .map(this::toResponse)
            .sorted(Comparator.comparing(LeaveBalanceResponse::getLeaveType))
            .collect(Collectors.toList());
    }

    @Override
    public List<LeaveBalanceResponse> getAllBalancesForYear(Integer year) {
        int targetYear = (year != null) ? year : LocalDateTime.now().getYear();
        return leaveBalanceRepository.findByYear(targetYear)
            .stream().map(this::toResponse)
            .collect(Collectors.toList());
    }

    // ── Bulk initialize ───────────────────────────────────────

    @Override
    @Transactional
    public InitializationResultResponse initializeBalances(InitializeBalancesRequest request) {
        log.info("Initializing leave balances for year {} — employees: {}",
            request.getYear(),
            request.getEmployeeIds() == null ? "ALL" : request.getEmployeeIds().size());

        // ── Get active leave types ─────────────────────────────
        List<LeaveType> leaveTypes = leaveTypeRepository
            .findByIsActiveOrderBySortOrderAsc(1);

        if (leaveTypes.isEmpty()) {
            throw new BusinessRuleException("NO_LEAVE_TYPES",
                "No active leave types configured. " +
                "Please add leave types in Admin Config first.");
        }

        // ── Get target employees ───────────────────────────────
        List<Employee> employees;
        if (request.getEmployeeIds() != null && !request.getEmployeeIds().isEmpty()) {
            employees = employeeRepository.findAllById(request.getEmployeeIds());
        } else {
            employees = employeeRepository.findAll()
                .stream()
                .filter(e -> Boolean.TRUE.equals(e.getIsActive()))
                .collect(Collectors.toList());
        }

        if (employees.isEmpty()) {
            throw new BusinessRuleException("NO_EMPLOYEES",
                "No active employees found to initialize.");
        }

        // ── Find employees already having balances ─────────────
        Set<Long> existingEmployeeIds = new HashSet<>();
        if (Boolean.TRUE.equals(request.getSkipExisting())) {
            existingEmployeeIds = new HashSet<>(
                leaveBalanceRepository.findEmployeeIdsWithBalancesForYear(request.getYear())
            );
        }

        int initialized = 0;
        int skipped     = 0;
        int errors      = 0;
        List<String> errorMessages = new ArrayList<>();

        for (Employee emp : employees) {
            // ── Skip if already initialized ───────────────────
            if (Boolean.TRUE.equals(request.getSkipExisting())
                    && existingEmployeeIds.contains(emp.getId())) {
                skipped++;
                continue;
            }

            try {
                createBalancesForEmployee(
                    emp.getId(), request.getYear(), leaveTypes,
                    Boolean.TRUE.equals(request.getApplyCarryForward())
                );
                initialized++;
            } catch (Exception ex) {
                errors++;
                errorMessages.add(
                    "EMP-" + emp.getEmployeeCode() + ": " + ex.getMessage());
                log.warn("Failed to initialize balances for employee {}: {}",
                    emp.getId(), ex.getMessage());
            }
        }

        log.info("Initialization complete — year={}, initialized={}, skipped={}, errors={}",
            request.getYear(), initialized, skipped, errors);

        return InitializationResultResponse.builder()
            .year(request.getYear())
            .totalEmployees(employees.size())
            .initializedCount(initialized)
            .skippedCount(skipped)
            .errorCount(errors)
            .errors(errorMessages)
            .processedAt(LocalDateTime.now().toString())
            .build();
    }

    // ── Initialize for single employee ────────────────────────

    @Override
    @Transactional
    public List<LeaveBalanceResponse> initializeForEmployee(Long employeeId, Integer year) {
        log.info("Initializing balances for employee {} year {}", employeeId, year);

        if (!employeeRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException("Employee", "id", employeeId);
        }

        List<LeaveType> leaveTypes = leaveTypeRepository
            .findByIsActiveOrderBySortOrderAsc(1);

        int targetYear = (year != null) ? year : LocalDateTime.now().getYear();
        createBalancesForEmployee(employeeId, targetYear, leaveTypes, true);

        return leaveBalanceRepository
            .findByEmployeeIdAndYear(employeeId, targetYear)
            .stream().map(this::toResponse)
            .collect(Collectors.toList());
    }

    // ── Adjust balance ────────────────────────────────────────

    @Override
    @Transactional
    public LeaveBalanceResponse adjustBalance(Long employeeId, AdjustBalanceRequest request) {
        log.info("Adjusting {} balance for employee {} — type={} days={} reason={}",
            request.getAdjustmentType(), employeeId,
            request.getLeaveTypeCode(), request.getDays(), request.getReason());

        if (!employeeRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException("Employee", "id", employeeId);
        }

        LeaveBalance balance = leaveBalanceRepository
            .findByEmployeeIdAndLeaveTypeCodeAndYear(
                employeeId, request.getLeaveTypeCode(), request.getYear())
            .orElseThrow(() -> new ResourceNotFoundException(
                "LeaveBalance",
                "employeeId+leaveType+year",
                employeeId + "/" + request.getLeaveTypeCode() + "/" + request.getYear()));

        double oldTotal = balance.getTotalDays();

        switch (request.getAdjustmentType()) {
            case "GRANT"  -> balance.setTotalDays(balance.getTotalDays() + request.getDays());
            case "DEDUCT" -> {
                double newTotal = balance.getTotalDays() - request.getDays();
                if (newTotal < 0) {
                    throw new BusinessRuleException("INVALID_DEDUCTION",
                        "Cannot deduct " + request.getDays() +
                        " days. Current total is " + balance.getTotalDays() + " days.");
                }
                balance.setTotalDays(newTotal);
            }
            case "RESET"  -> balance.setTotalDays(request.getDays());
        }

        balance.setUpdatedAt(LocalDateTime.now());
        LeaveBalance saved = leaveBalanceRepository.save(balance);

        log.info("Balance adjusted — employee={} type={} year={} {} → {}",
            employeeId, request.getLeaveTypeCode(), request.getYear(),
            oldTotal, saved.getTotalDays());

        return toResponse(saved);
    }

    // ── Private helpers ───────────────────────────────────────

    private void createBalancesForEmployee(
            Long employeeId, Integer year,
            List<LeaveType> leaveTypes, boolean applyCarryForward) {

        for (LeaveType lt : leaveTypes) {
            // Skip if balance already exists for this type+year
            if (leaveBalanceRepository.existsByEmployeeIdAndLeaveTypeCodeAndYear(
                    employeeId, lt.getCode(), year)) {
                continue;
            }

            double totalDays = lt.getDefaultDays() != null
                ? lt.getDefaultDays().doubleValue() : 0.0;

            double carriedDays = 0.0;

            // ── Carry forward annual leave from previous year ──
            if (applyCarryForward
                    && lt.getIsCarryForward() == 1
                    && lt.getMaxCarryDays() != null
                    && lt.getMaxCarryDays().doubleValue() > 0) {

                Optional<LeaveBalance> prevYear = leaveBalanceRepository
                    .findByEmployeeIdAndLeaveTypeCodeAndYear(
                        employeeId, lt.getCode(), year - 1);

                if (prevYear.isPresent()) {
                    double available = prevYear.get().getAvailableDays();
                    double maxCarry  = lt.getMaxCarryDays().doubleValue();
                    carriedDays = Math.min(available, maxCarry);
                    totalDays  += carriedDays;
                }
            }

            LeaveBalance balance = LeaveBalance.builder()
                .employeeId(employeeId)
                .leaveTypeCode(lt.getCode())
                .year(year)
                .totalDays(totalDays)
                .usedDays(0.0)
                .pendingDays(0.0)
                .build();


            leaveBalanceRepository.save(balance);
            log.debug("Created balance — emp={} type={} year={} total={}",
                employeeId, lt.getCode(), year, totalDays);
        }
    }

    private LeaveBalanceResponse toResponse(LeaveBalance lb) {
        // Enrich with leave type name if available
        String leaveTypeName   = lb.getLeaveTypeCode();
        String leaveTypeNameAr = null;
        Boolean isPaid         = null;
        Boolean isCarryForward = null;
        Boolean requiresDocument = null;
        Integer docMaxFileSizeMb = null;
        String  docAllowedExtensions = null;

        try {
            Optional<LeaveType> lt = leaveTypeRepository
                .findByCodeIgnoreCase(lb.getLeaveTypeCode());
            if (lt.isPresent()) {
                leaveTypeName   = lt.get().getNameEn();
                leaveTypeNameAr = lt.get().getNameAr();
                isPaid          = lt.get().getIsPaid() == 1;
                isCarryForward  = lt.get().getIsCarryForward() == 1;
                requiresDocument = lt.get().getRequiresDocument() != null
                    && lt.get().getRequiresDocument() == 1;
                docMaxFileSizeMb     = uploadLimits.effectiveMb(lt.get().resolveDocMaxFileSizeMb());
                docAllowedExtensions = lt.get().resolveDocAllowedExtensions();
            }
        } catch (Exception ignored) { /* graceful fallback */ }

        return LeaveBalanceResponse.builder()
            .balanceId(lb.getBalanceId())
            .employeeId(lb.getEmployeeId())
            .leaveType(lb.getLeaveTypeCode())
            .leaveTypeName(leaveTypeName)
            .leaveTypeNameAr(leaveTypeNameAr)
            .isPaid(isPaid)
            .isCarryForward(isCarryForward)
            .requiresDocument(requiresDocument)
            .docMaxFileSizeMb(docMaxFileSizeMb)
            .docAllowedExtensions(docAllowedExtensions)
            .year(lb.getYear())
            .totalDays(lb.getTotalDays())
            .usedDays(lb.getUsedDays())
            .pendingDays(lb.getPendingDays())
            .availableDays(lb.getAvailableDays())
            .createdBy(lb.getCreatedBy())
            .createdAt(lb.getCreatedAt() != null ? lb.getCreatedAt().toString() : null)
            .updatedAt(lb.getUpdatedAt() != null ? lb.getUpdatedAt().toString() : null)
            .build();
    }
}
