package com.hrms.leave.service.impl;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.enums.Gender;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.config.UploadLimits;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.leave.dto.request.AdjustBalanceRequest;
import com.hrms.leave.dto.request.InitializeBalancesRequest;
import com.hrms.leave.dto.response.InitializationResultResponse;
import com.hrms.leave.dto.response.LeaveBalanceResponse;
import com.hrms.leave.entity.LeaveBalance;
import com.hrms.leave.entity.LeaveType;
import com.hrms.leave.repository.LeaveBalanceRepository;
import com.hrms.leave.repository.LeaveTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Yearly balances: how they are created (defaults, carry-forward) and how HR adjusts them. */
class LeaveBalanceServiceImplTest {

    LeaveBalanceRepository balances  = mock(LeaveBalanceRepository.class);
    LeaveTypeRepository    types     = mock(LeaveTypeRepository.class);
    EmployeeRepository     employees = mock(EmployeeRepository.class);
    EmployeeAccessGuard    guard     = mock(EmployeeAccessGuard.class);
    UploadLimits           limits    = mock(UploadLimits.class);
    LeaveBalanceServiceImpl service;

    LeaveType annual = LeaveType.builder().code("ANNUAL").nameEn("Annual").defaultDays(new BigDecimal("21"))
            .isCarryForward(1).maxCarryDays(new BigDecimal("5")).isActive(1).build();
    LeaveType sick = LeaveType.builder().code("SICK").nameEn("Sick").defaultDays(new BigDecimal("15"))
            .isCarryForward(0).maxCarryDays(BigDecimal.ZERO).isActive(1).build();

    @BeforeEach
    void setUp() {
        service = new LeaveBalanceServiceImpl(balances, types, employees, guard, limits);
        when(types.findByIsActiveOrderBySortOrderAsc(1)).thenReturn(List.of(annual, sick));
        when(employees.existsById(5L)).thenReturn(true);
        when(balances.save(any(LeaveBalance.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static Employee person(long id, boolean active) {
        return Employee.builder().id(id).employeeCode("EMP-" + id).isActive(active).build();
    }

    private static InitializeBalancesRequest init(Boolean skipExisting, Boolean carry, Long... ids) {
        return InitializeBalancesRequest.builder().year(2027).employeeIds(ids.length == 0 ? null : List.of(ids))
                .skipExisting(skipExisting).applyCarryForward(carry).build();
    }

    private List<LeaveBalance> saved(int times) {
        ArgumentCaptor<LeaveBalance> c = ArgumentCaptor.forClass(LeaveBalance.class);
        verify(balances, times(times)).save(c.capture());
        return c.getAllValues();
    }

    private static LeaveBalance find(List<LeaveBalance> list, long employee, String code) {
        return list.stream().filter(b -> b.getEmployeeId() == employee && b.getLeaveTypeCode().equals(code))
                .findFirst().orElseThrow();
    }

    // ── Creating the year's balances ─────────────────────────

    @Test
    void everyActiveTypeGetsItsDefaultDaysAndNothingUsed() {
        when(employees.findAllById(List.of(5L))).thenReturn(List.of(person(5, true)));

        InitializationResultResponse r = service.initializeBalances(init(true, false, 5L));

        List<LeaveBalance> made = saved(2);
        assertEquals(21.0, find(made, 5, "ANNUAL").getTotalDays());
        assertEquals(15.0, find(made, 5, "SICK").getTotalDays());
        assertEquals(0.0, find(made, 5, "ANNUAL").getUsedDays());
        assertEquals(0.0, find(made, 5, "ANNUAL").getPendingDays());
        assertEquals(2027, find(made, 5, "ANNUAL").getYear());
        assertEquals(1, r.getInitializedCount());
    }

    @Test
    void aBalanceThatAlreadyExistsForATypeIsNeverOverwritten() {
        when(employees.findAllById(List.of(5L))).thenReturn(List.of(person(5, true)));
        when(balances.existsByEmployeeIdAndLeaveTypeCodeAndYear(5L, "ANNUAL", 2027)).thenReturn(true);

        service.initializeBalances(init(false, false, 5L));

        List<LeaveBalance> made = saved(1);
        assertEquals("SICK", made.get(0).getLeaveTypeCode());
    }

    @Test
    void employeesWithBalancesAreSkippedWhenAsked() {
        when(employees.findAllById(List.of(5L, 6L))).thenReturn(List.of(person(5, true), person(6, true)));
        when(balances.findEmployeeIdsWithBalancesForYear(2027)).thenReturn(List.of(5L));

        InitializationResultResponse r = service.initializeBalances(init(true, false, 5L, 6L));

        assertEquals(1, r.getSkippedCount());
        assertEquals(1, r.getInitializedCount());
        assertEquals(2, r.getTotalEmployees());
        saved(2);   // only employee 6's two types
    }

    @Test
    void withoutAnEmployeeListEveryActiveEmployeeIsInitialized() {
        when(employees.findAll()).thenReturn(List.of(person(5, true), person(6, false)));

        InitializationResultResponse r = service.initializeBalances(init(true, false));

        assertEquals(1, r.getTotalEmployees(), "the inactive employee is left out");
        saved(2);
    }

    @Test
    void oneEmployeesFailureIsReportedAndTheOthersStillGetTheirBalances() {
        when(employees.findAllById(List.of(5L, 6L))).thenReturn(List.of(person(5, true), person(6, true)));
        when(balances.existsByEmployeeIdAndLeaveTypeCodeAndYear(eq(5L), any(), eq(2027)))
                .thenThrow(new IllegalStateException("db down"));

        InitializationResultResponse r = service.initializeBalances(init(false, false, 5L, 6L));

        assertEquals(1, r.getErrorCount());
        assertEquals(1, r.getInitializedCount());
        assertTrue(r.getErrors().get(0).contains("db down"));
    }

    @Test
    void noActiveLeaveTypesOrNoEmployeesIsRefused() {
        when(types.findByIsActiveOrderBySortOrderAsc(1)).thenReturn(List.of());
        assertEquals("NO_LEAVE_TYPES", assertThrows(BusinessRuleException.class,
                () -> service.initializeBalances(init(true, false, 5L))).getRuleCode());

        when(types.findByIsActiveOrderBySortOrderAsc(1)).thenReturn(List.of(annual));
        when(employees.findAllById(List.of(5L))).thenReturn(List.of());
        assertEquals("NO_EMPLOYEES", assertThrows(BusinessRuleException.class,
                () -> service.initializeBalances(init(true, false, 5L))).getRuleCode());
    }

    // ── Carry-forward ────────────────────────────────────────

    private void lastYearAvailable(double total, double used) {
        when(balances.findByEmployeeIdAndLeaveTypeCodeAndYear(5L, "ANNUAL", 2026)).thenReturn(Optional.of(
                LeaveBalance.builder().employeeId(5L).leaveTypeCode("ANNUAL").year(2026)
                        .totalDays(total).usedDays(used).pendingDays(0.0).build()));
        when(employees.findAllById(List.of(5L))).thenReturn(List.of(person(5, true)));
    }

    @Test
    void unusedDaysCarryForwardUpToTheTypesLimit() {
        lastYearAvailable(21.0, 10.0);      // 11 unused, limit 5

        service.initializeBalances(init(false, true, 5L));

        assertEquals(26.0, find(saved(2), 5, "ANNUAL").getTotalDays(), "21 default + 5 carried");
    }

    @Test
    void lessThanTheLimitCarriesOnlyWhatIsLeft() {
        lastYearAvailable(21.0, 19.0);      // 2 unused

        service.initializeBalances(init(false, true, 5L));

        assertEquals(23.0, find(saved(2), 5, "ANNUAL").getTotalDays());
    }

    @Test
    void nothingCarriesWhenTheSwitchIsOffTheTypeDoesNotAllowItOrThereIsNoLastYear() {
        lastYearAvailable(21.0, 10.0);
        service.initializeBalances(init(false, false, 5L));          // switch off
        assertEquals(21.0, find(saved(2), 5, "ANNUAL").getTotalDays());

        clearInvocations(balances);
        annual.setIsCarryForward(0);
        service.initializeBalances(init(false, true, 5L));           // type does not allow it
        assertEquals(21.0, find(saved(2), 5, "ANNUAL").getTotalDays());

        clearInvocations(balances);
        annual.setIsCarryForward(1);
        when(balances.findByEmployeeIdAndLeaveTypeCodeAndYear(5L, "ANNUAL", 2026)).thenReturn(Optional.empty());
        service.initializeBalances(init(false, true, 5L));           // no row for last year
        assertEquals(21.0, find(saved(2), 5, "ANNUAL").getTotalDays());
    }

    @Test
    void aTypeWithNoCarryLimitCarriesNothing() {
        lastYearAvailable(21.0, 10.0);
        annual.setMaxCarryDays(BigDecimal.ZERO);

        service.initializeBalances(init(false, true, 5L));

        assertEquals(21.0, find(saved(2), 5, "ANNUAL").getTotalDays());
    }

    // ── HR adjustments ───────────────────────────────────────

    private LeaveBalance current = LeaveBalance.builder().employeeId(5L).leaveTypeCode("ANNUAL").year(2027)
            .totalDays(21.0).usedDays(0.0).pendingDays(0.0).build();

    private LeaveBalanceResponse adjust(String type, double days) {
        when(balances.findByEmployeeIdAndLeaveTypeCodeAndYear(5L, "ANNUAL", 2027)).thenReturn(Optional.of(current));
        return service.adjustBalance(5L, AdjustBalanceRequest.builder().leaveTypeCode("ANNUAL").year(2027)
                .adjustmentType(type).days(days).reason("HR correction").build());
    }

    @Test
    void grantAddsDeductSubtractsAndResetSetsTheTotal() {
        assertEquals(26.0, adjust("GRANT", 5).getTotalDays());
        assertEquals(20.0, adjust("DEDUCT", 6).getTotalDays());
        assertEquals(30.0, adjust("RESET", 30).getTotalDays());
    }

    @Test
    void youCannotDeductMoreThanTheTotal() {
        var ex = assertThrows(BusinessRuleException.class, () -> adjust("DEDUCT", 21.5));

        assertEquals("INVALID_DEDUCTION", ex.getRuleCode());
        assertEquals(21.0, current.getTotalDays());
        verify(balances, never()).save(any());
    }

    @Test
    void youCannotDeductOrResetBelowDaysAlreadyUsedOrPending() {
        current.setUsedDays(8.0);
        current.setPendingDays(5.0);          // 13 committed, 8 available

        assertEquals("INVALID_DEDUCTION", assertThrows(BusinessRuleException.class,
                () -> adjust("DEDUCT", 9)).getRuleCode());
        assertEquals("INVALID_DEDUCTION", assertThrows(BusinessRuleException.class,
                () -> adjust("RESET", 12)).getRuleCode());
        verify(balances, never()).save(any());

        assertEquals(13.0, adjust("DEDUCT", 8).getTotalDays(), "down to exactly the committed days is fine");
    }

    @Test
    void deductingExactlyTheTotalIsAllowed() {
        assertEquals(0.0, adjust("DEDUCT", 21).getTotalDays());
    }

    @Test
    void adjustingAMissingEmployeeOrBalanceIsNotFound() {
        when(employees.existsById(99L)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.adjustBalance(99L,
                AdjustBalanceRequest.builder().leaveTypeCode("ANNUAL").year(2027).adjustmentType("GRANT").days(1.0).build()));

        when(balances.findByEmployeeIdAndLeaveTypeCodeAndYear(5L, "ANNUAL", 2030)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.adjustBalance(5L,
                AdjustBalanceRequest.builder().leaveTypeCode("ANNUAL").year(2030).adjustmentType("GRANT").days(1.0).build()));
    }

    // ── Reading ──────────────────────────────────────────────

    @Test
    void anUnknownEmployeesBalancesAreNotFoundAndAKnownOnesAreSortedByType() {
        when(employees.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getEmployeeBalances(99L, 2027));
        when(employees.findById(5L)).thenReturn(Optional.of(person(5, true)));

        when(balances.findByEmployeeIdAndYear(5L, 2027)).thenReturn(List.of(
                LeaveBalance.builder().employeeId(5L).leaveTypeCode("SICK").year(2027).totalDays(15.0).usedDays(0.0).pendingDays(0.0).build(),
                LeaveBalance.builder().employeeId(5L).leaveTypeCode("ANNUAL").year(2027).totalDays(21.0).usedDays(0.0).pendingDays(0.0).build()));

        List<LeaveBalanceResponse> r = service.getEmployeeBalances(5L, 2027);

        assertEquals(List.of("ANNUAL", "SICK"), r.stream().map(LeaveBalanceResponse::getLeaveType).toList());
    }

    // ── Leave types the employee's gender cannot take ──────────

    private LeaveBalance row(String code, double used, double pending) {
        return LeaveBalance.builder().employeeId(5L).leaveTypeCode(code).year(2027).totalDays(90.0)
                .usedDays(used).pendingDays(pending).build();
    }

    private List<String> listedFor(Gender gender, LeaveBalance... rows) {
        when(employees.findById(5L)).thenReturn(Optional.of(
                Employee.builder().id(5L).employeeCode("EMP-5").gender(gender).build()));
        when(balances.findByEmployeeIdAndYear(5L, 2027)).thenReturn(List.of(rows));
        return service.getEmployeeBalances(5L, 2027).stream().map(LeaveBalanceResponse::getLeaveType).toList();
    }

    @Test
    void aFemaleOnlyTypeIsNotListedForAManButIsForAWoman() {
        LeaveType maternity = LeaveType.builder().code("MATERNITY").nameEn("Maternity").applicableGender("FEMALE").build();
        when(types.findByCodeIgnoreCase("MATERNITY")).thenReturn(Optional.of(maternity));

        assertEquals(List.of("ANNUAL"), listedFor(Gender.MALE, row("ANNUAL", 0, 0), row("MATERNITY", 0, 0)));
        assertEquals(List.of("ANNUAL", "MATERNITY"), listedFor(Gender.FEMALE, row("ANNUAL", 0, 0), row("MATERNITY", 0, 0)));
    }

    @Test
    void anEmployeeWithNoGenderDoesNotSeeRestrictedTypes() {
        LeaveType maternity = LeaveType.builder().code("MATERNITY").nameEn("Maternity").applicableGender("FEMALE").build();
        when(types.findByCodeIgnoreCase("MATERNITY")).thenReturn(Optional.of(maternity));

        assertEquals(List.of(), listedFor(null, row("MATERNITY", 0, 0)));
    }

    @Test
    void daysAlreadyUsedOrPendingOnARestrictedTypeAreNeverHidden() {
        LeaveType maternity = LeaveType.builder().code("MATERNITY").nameEn("Maternity").applicableGender("FEMALE").build();
        when(types.findByCodeIgnoreCase("MATERNITY")).thenReturn(Optional.of(maternity));

        assertEquals(List.of("MATERNITY"), listedFor(Gender.MALE, row("MATERNITY", 3, 0)));
        assertEquals(List.of("MATERNITY"), listedFor(Gender.MALE, row("MATERNITY", 0, 2)));
    }
}
