package com.hrms.payroll.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.payroll.dto.request.EmployeeLoanRequest;
import com.hrms.payroll.dto.request.LoanStatusRequest;
import com.hrms.payroll.dto.response.EmployeeLoanResponse;
import com.hrms.payroll.entity.EmployeeLoan;
import com.hrms.payroll.entity.EmployeeLoan.LoanStatus;
import com.hrms.payroll.entity.SalaryComponent;
import com.hrms.payroll.enums.CalculationType;
import com.hrms.payroll.enums.ComponentType;
import com.hrms.payroll.repository.EmployeeLoanRepository;
import com.hrms.payroll.repository.SalaryComponentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** The loan register's rules: who and what can be recorded, the instalment maths, closing and cancelling. */
class EmployeeLoanServiceImplTest {

    EmployeeLoanRepository loans = mock(EmployeeLoanRepository.class);
    SalaryComponentRepository components = mock(SalaryComponentRepository.class);
    EmployeeRepository employees = mock(EmployeeRepository.class);
    AuditTrail audit = mock(AuditTrail.class);
    EmployeeLoanServiceImpl service = new EmployeeLoanServiceImpl(loans, components, employees, audit);

    SalaryComponent emergency;
    Employee employee;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setId(5L);
        employee.setEmployeeCode("EMP-5");
        employee.setFirstName("Nyibol");
        employee.setLastName("Akec");
        when(employees.findByIdForUpdate(5L)).thenReturn(Optional.of(employee));
        emergency = SalaryComponent.builder().componentId(21L).componentCode("ADV_EMERGENCY").componentName("Emergency Advance")
            .componentNameAr("x").componentType(ComponentType.DEDUCTION).calcType(CalculationType.FIXED).isLoan(1).build();
        when(components.findById(21L)).thenReturn(Optional.of(emergency));
        when(loans.findNextSequenceValue()).thenReturn(700L);
        when(loans.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }

    private EmployeeLoanRequest request(String principal, int months, Integer paid) {
        return new EmployeeLoanRequest(5L, 21L, new BigDecimal(principal), months, "2026-12", paid, " approved by GM ");
    }

    private void assertRefused(String rule, EmployeeLoanRequest req) {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.create(req));
        assertEquals(rule, ex.getRuleCode());
        verify(loans, never()).saveAndFlush(any());
    }

    @Test
    void theInstalmentIsPrincipalOverMonths_roundedDown_andTheLastOneTakesTheRest() {
        // The sheet's =12122140/36
        EmployeeLoanResponse r = service.create(request("12122140", 36, null));
        assertEquals(0, new BigDecimal("336726.11").compareTo(r.monthlyInstalment()));
        assertEquals(0, new BigDecimal("336726.15").compareTo(r.finalInstalment()));
        assertEquals(0, new BigDecimal("12122140").compareTo(r.remainingBalance()));
        assertEquals("2026-12-01", r.startMonth());
        assertEquals("2029-11-01", r.lastMonth());
        assertEquals("ACTIVE", r.status());

        var saved = ArgumentCaptor.forClass(EmployeeLoan.class);
        verify(loans).saveAndFlush(saved.capture());
        assertEquals(LocalDate.of(2026, 12, 1), saved.getValue().getStartMonth());
        assertEquals("approved by GM", saved.getValue().getRemarks());
        assertEquals(0, saved.getValue().getInstalmentsPaid());
        // Ids only in the audit detail, never amounts
        verify(audit).record(eq("LOAN_CREATED"), eq("EMPLOYEE"), eq(5L), eq("loan 700, type 21"));
    }

    @Test
    void aRunningLoanStartsFromTheInstalmentsAlreadyPaid() {
        EmployeeLoanResponse r = service.create(request("12122140", 36, 10));
        assertEquals(10, r.instalmentsPaid());
        // 12,122,140 - 10 x 336,726.11
        assertEquals(0, new BigDecimal("8754878.90").compareTo(r.remainingBalance()));
    }

    @Test
    void aFullyRepaidOrTooSmallLoanIsRefused() {
        assertRefused("LOAN_ALREADY_REPAID", request("1000", 12, 12));
        assertRefused("LOAN_INSTALMENT_TOO_SMALL", request("0.50", 60, null));
    }

    @Test
    void anInactiveEmployeeOrAnythingButAnActiveLoanTypeIsRefused() {
        employee.setIsActive(false);
        assertRefused("EMP_INACTIVE", request("1000", 10, null));
        employee.setIsActive(true);

        emergency.setIsLoan(0);
        assertRefused("LOAN_TYPE_INVALID", request("1000", 10, null));
        emergency.setIsLoan(1);
        emergency.setIsActive(0);
        assertRefused("LOAN_TYPE_INVALID", request("1000", 10, null));

        when(employees.findByIdForUpdate(9L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.create(
            new EmployeeLoanRequest(9L, 21L, new BigDecimal("1000"), 10, "2026-12", null, null)));
    }

    @Test
    void oneRunningLoanPerType_checkedFirst_andTheDatabaseIndexCatchesARace() {
        when(loans.existsByEmployeeIdAndComponentIdAndStatus(5L, 21L, LoanStatus.ACTIVE)).thenReturn(true);
        assertRefused("LOAN_TYPE_ALREADY_RUNNING", request("1000", 10, null));

        when(loans.existsByEmployeeIdAndComponentIdAndStatus(5L, 21L, LoanStatus.ACTIVE)).thenReturn(false);
        when(loans.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("UQ_ELOAN_ONE_ACTIVE"));
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.create(request("1000", 10, null)));
        assertEquals("LOAN_TYPE_ALREADY_RUNNING", ex.getRuleCode());
        verify(audit, never()).record(any(), any(), any(), any());
    }

    private EmployeeLoan running() {
        EmployeeLoan loan = EmployeeLoan.builder().loanId(700L).employeeId(5L).componentId(21L)
            .principal(new BigDecimal("1000")).instalmentCount(10).monthlyInstalment(new BigDecimal("100"))
            .startMonth(LocalDate.of(2026, 12, 1)).instalmentsPaid(3).status(LoanStatus.ACTIVE).version(2L).build();
        when(loans.findById(700L)).thenReturn(Optional.of(loan));
        when(employees.findById(5L)).thenReturn(Optional.of(employee));
        return loan;
    }

    @Test
    void closingRecordsWhoWhenAndWhy() {
        EmployeeLoan loan = running();
        EmployeeLoanResponse r = service.close(700L, new LoanStatusRequest(2L, " repaid in cash "));
        assertEquals("CLOSED", r.status());
        assertEquals("repaid in cash", loan.getStatusReason());
        assertNotNull(loan.getStatusChangedAt());
        assertEquals(0, new BigDecimal("700").compareTo(r.remainingBalance()));
        verify(audit).record(eq("LOAN_CLOSED"), eq("EMPLOYEE"), eq(5L), eq("loan 700"));
    }

    @Test
    void onlyARunningLoanCanBeClosedOrCancelled_andAStaleScreenIsRefused() {
        EmployeeLoan loan = running();
        assertThrows(RuntimeException.class, () -> service.cancel(700L, new LoanStatusRequest(1L, "typo")));
        loan.setStatus(LoanStatus.CANCELLED);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.close(700L, new LoanStatusRequest(2L, "repaid")));
        assertEquals("LOAN_NOT_ACTIVE", ex.getRuleCode());
        verify(loans, never()).saveAndFlush(any());
    }

    @Test
    void searchEscapesTheWildcardsOfTheText() {
        when(loans.search(any(), any(), any(), any(), any())).thenReturn(org.springframework.data.domain.Page.empty());
        service.search(" 50%_a\\ ", null, null, null, org.springframework.data.domain.PageRequest.of(0, 20));
        verify(loans).search(eq("50\\%\\_a\\\\"), any(), any(), any(), any());
    }
}
