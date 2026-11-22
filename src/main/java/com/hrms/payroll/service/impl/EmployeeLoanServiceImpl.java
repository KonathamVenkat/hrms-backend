package com.hrms.payroll.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.audit.CurrentAuditor;
import com.hrms.common.dto.PagedResponse;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.service.impl.Versions;
import com.hrms.payroll.dto.request.EmployeeLoanRequest;
import com.hrms.payroll.dto.request.LoanStatusRequest;
import com.hrms.payroll.dto.response.EmployeeLoanResponse;
import com.hrms.payroll.dto.response.LoanTypeResponse;
import com.hrms.payroll.entity.EmployeeLoan;
import com.hrms.payroll.entity.EmployeeLoan.LoanStatus;
import com.hrms.payroll.entity.SalaryComponent;
import com.hrms.payroll.enums.ComponentType;
import com.hrms.payroll.repository.EmployeeLoanRepository;
import com.hrms.payroll.repository.SalaryComponentRepository;
import com.hrms.payroll.repository.projection.EmployeeLoanRow;
import com.hrms.payroll.service.EmployeeLoanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeLoanServiceImpl implements EmployeeLoanService {

    private static final String TARGET = "EMPLOYEE";

    private final EmployeeLoanRepository loans;
    private final SalaryComponentRepository components;
    private final EmployeeRepository employees;
    private final AuditTrail audit;

    @Override
    public PagedResponse<EmployeeLoanResponse> search(String q, Long componentId, String status, Long employeeId,
                                                      Pageable pageable) {
        String text = q == null || q.isBlank() ? null : q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return PagedResponse.from(loans.search(text, componentId, status, employeeId, pageable).map(EmployeeLoanServiceImpl::toResponse));
    }

    @Override
    public List<LoanTypeResponse> loanTypes() {
        return components.findLoanTypes().stream()
            .map(c -> new LoanTypeResponse(c.getComponentId(), c.getComponentCode(), c.getComponentName(), c.getComponentNameAr()))
            .toList();
    }

    @Override
    @Transactional
    public EmployeeLoanResponse create(EmployeeLoanRequest request) {
        // Two loans of one type for one employee at once: the second waits here and then sees the first.
        Employee employee = employees.findByIdForUpdate(request.employeeId())
            .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", request.employeeId()));
        if (!Boolean.TRUE.equals(employee.getIsActive())) {
            throw new BusinessRuleException("EMP_INACTIVE", "This employee is deactivated. A loan cannot be recorded.");
        }
        SalaryComponent type = components.findById(request.componentId())
            .filter(c -> c.getIsLoan() == 1 && c.getIsActive() == 1 && c.getComponentType() == ComponentType.DEDUCTION)
            .orElseThrow(() -> new BusinessRuleException("LOAN_TYPE_INVALID", "Choose an active loan type."));
        int count = request.instalmentCount();
        int paid = request.instalmentsPaid() == null ? 0 : request.instalmentsPaid();
        if (paid >= count) {
            throw new BusinessRuleException("LOAN_ALREADY_REPAID",
                "Instalments already paid must be fewer than the number of months; a repaid loan is not recorded.");
        }
        // Rounded down to the cent; the last instalment takes what is left, so it is never smaller than the others.
        BigDecimal instalment = request.principal().divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        if (instalment.signum() <= 0) {
            throw new BusinessRuleException("LOAN_INSTALMENT_TOO_SMALL",
                "The principal is too small for that many months: each instalment must be at least 0.01.");
        }
        if (loans.existsByEmployeeIdAndComponentIdAndStatus(employee.getId(), type.getComponentId(), LoanStatus.ACTIVE)) {
            throw alreadyRunning(type);
        }

        String auditor = CurrentAuditor.name();
        LocalDateTime now = LocalDateTime.now();
        EmployeeLoan loan = EmployeeLoan.builder()
            .loanId(loans.findNextSequenceValue())
            .employeeId(employee.getId())
            .componentId(type.getComponentId())
            .principal(request.principal())
            .instalmentCount(count)
            .monthlyInstalment(instalment)
            .startMonth(YearMonth.parse(request.startMonth()).atDay(1))
            .instalmentsPaid(paid)
            .status(LoanStatus.ACTIVE)
            .remarks(trimToNull(request.remarks()))
            .build();
        loan.setCreatedBy(auditor);
        loan.setCreatedAt(now);
        loan.setUpdatedAt(now);
        try {
            loans.saveAndFlush(loan);
        } catch (DataIntegrityViolationException e) {
            throw alreadyRunning(type); // UQ_ELOAN_ONE_ACTIVE: another session recorded one in between
        }
        log.info("Loan recorded — loanId={}, employeeId={}, componentId={}", loan.getLoanId(), employee.getId(), type.getComponentId());
        // Ids only: amounts are pay data.
        audit.record("LOAN_CREATED", TARGET, employee.getId(), "loan " + loan.getLoanId() + ", type " + type.getComponentId());
        return toResponse(loan, employee, type);
    }

    @Override
    @Transactional
    public EmployeeLoanResponse close(Long loanId, LoanStatusRequest request) {
        return endLoan(loanId, request, LoanStatus.CLOSED, "LOAN_CLOSED");
    }

    @Override
    @Transactional
    public EmployeeLoanResponse cancel(Long loanId, LoanStatusRequest request) {
        return endLoan(loanId, request, LoanStatus.CANCELLED, "LOAN_CANCELLED");
    }

    @Override
    public BigDecimal monthlyInstalments(Long employeeId, LocalDate month) {
        return loans.monthlyInstalments(employeeId, month.withDayOfMonth(1));
    }

    private EmployeeLoanResponse endLoan(Long loanId, LoanStatusRequest request, LoanStatus to, String action) {
        EmployeeLoan loan = loans.findById(loanId).orElseThrow(() -> new ResourceNotFoundException("Loan", "id", loanId));
        Versions.requireCurrent(request.version(), loan.getVersion());
        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new BusinessRuleException("LOAN_NOT_ACTIVE", "Only a running loan can be closed or cancelled.");
        }
        String auditor = CurrentAuditor.name();
        LocalDateTime now = LocalDateTime.now();
        loan.setStatus(to);
        loan.setStatusReason(request.reason().trim());
        loan.setStatusChangedBy(auditor);
        loan.setStatusChangedAt(now);
        loan.setUpdatedBy(auditor);
        loan.setUpdatedAt(now);
        loans.saveAndFlush(loan);
        audit.record(action, TARGET, loan.getEmployeeId(), "loan " + loan.getLoanId());
        Employee employee = employees.findById(loan.getEmployeeId()).orElse(null);
        SalaryComponent type = components.findById(loan.getComponentId()).orElse(null);
        return toResponse(loan, employee, type);
    }

    private static BusinessRuleException alreadyRunning(SalaryComponent type) {
        return new BusinessRuleException("LOAN_TYPE_ALREADY_RUNNING",
            "This employee already has a running " + type.getComponentName()
                + ". Close it first, or record the new one under the second type (e.g. 'Emergency Advance 2').");
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static EmployeeLoanResponse toResponse(EmployeeLoanRow r) {
        LocalDate start = LocalDate.parse(r.getStartMonth());
        return response(r.getLoanId(), r.getEmployeeId(), r.getEmployeeCode(), r.getEmployeeName(), r.getComponentId(),
            r.getComponentCode(), r.getLoanTypeName(), r.getLoanTypeNameAr(), r.getPrincipal(), r.getInstalmentCount(),
            r.getMonthlyInstalment(), r.getInstalmentsPaid(), start, r.getStatus(), r.getStatusReason(), r.getRemarks(),
            r.getVersion());
    }

    private static EmployeeLoanResponse toResponse(EmployeeLoan l, Employee e, SalaryComponent c) {
        return response(l.getLoanId(), l.getEmployeeId(), e == null ? null : e.getEmployeeCode(),
            e == null ? null : (e.getFirstName() + " " + e.getLastName()).trim(), l.getComponentId(),
            c == null ? null : c.getComponentCode(), c == null ? null : c.getComponentName(),
            c == null ? null : c.getComponentNameAr(), l.getPrincipal(), l.getInstalmentCount(), l.getMonthlyInstalment(),
            l.getInstalmentsPaid(), l.getStartMonth(), l.getStatus().name(), l.getStatusReason(), l.getRemarks(),
            l.getVersion());
    }

    /** The figures every view shows: what is left, what the last instalment takes and when it falls. */
    static EmployeeLoanResponse response(Long loanId, Long employeeId, String employeeCode, String employeeName,
                                         Long componentId, String componentCode, String typeName, String typeNameAr,
                                         BigDecimal principal, int count, BigDecimal instalment, int paid, LocalDate start,
                                         String status, String reason, String remarks, Long version) {
        BigDecimal finalInstalment = principal.subtract(instalment.multiply(BigDecimal.valueOf(count - 1L)));
        BigDecimal remaining = paid >= count ? BigDecimal.ZERO.setScale(2)
            : principal.subtract(instalment.multiply(BigDecimal.valueOf(paid)));
        return new EmployeeLoanResponse(loanId, employeeId, employeeCode, employeeName, componentId, componentCode,
            typeName, typeNameAr, principal, count, instalment, finalInstalment, paid, remaining, start.toString(),
            start.plusMonths(count - 1L).toString(), status, reason, remarks, version);
    }
}
