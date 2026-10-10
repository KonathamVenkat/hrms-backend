package com.hrms.leave.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.enums.Gender;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.entity.EmployeeJobDetails;
import com.hrms.employee.entity.WorkShift;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.repository.WorkShiftRepository;
import com.hrms.leave.dto.request.ApproveLeaveRequest;
import com.hrms.leave.dto.request.CreateLeaveRequest;
import com.hrms.leave.dto.request.LeaveFilterRequest;
import com.hrms.leave.dto.response.LeaveRequestResponse;
import com.hrms.leave.entity.HolidayCalendar;
import com.hrms.leave.entity.LeaveBalance;
import com.hrms.leave.entity.LeaveRequest;
import com.hrms.leave.entity.LeaveRequestAttachmentContent;
import com.hrms.leave.entity.LeaveStatus;
import com.hrms.leave.entity.LeaveType;
import com.hrms.leave.repository.HolidayCalendarRepository;
import com.hrms.leave.service.WorkCalendar;
import com.hrms.leave.repository.LeaveBalanceRepository;
import com.hrms.leave.repository.LeaveRequestAttachmentContentRepository;
import com.hrms.leave.repository.LeaveRequestRepository;
import com.hrms.leave.dto.response.LeaveBalanceResponse;
import com.hrms.leave.repository.LeaveTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.web.multipart.MultipartFile;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Leave rules: what may be applied for, how days and balances move, and who may cancel or decide. */
class LeaveServiceImplTest {

    static final long EMPLOYEE = 5L;

    LeaveRequestRepository  requests   = mock(LeaveRequestRepository.class);
    LeaveBalanceRepository  balances   = mock(LeaveBalanceRepository.class);
    LeaveTypeRepository     types      = mock(LeaveTypeRepository.class);
    EmployeeRepository      employees  = mock(EmployeeRepository.class);
    HolidayCalendarRepository holidays = mock(HolidayCalendarRepository.class);
    EmployeeJobDetailsRepository jobDetails = mock(EmployeeJobDetailsRepository.class);
    WorkShiftRepository     shifts     = mock(WorkShiftRepository.class);
    EmployeeAccessGuard     guard      = mock(EmployeeAccessGuard.class);
    LeaveAttachmentStorage  storage    = mock(LeaveAttachmentStorage.class);
    LeaveRequestAttachmentContentRepository contents = mock(LeaveRequestAttachmentContentRepository.class);
    LeaveServiceImpl service;

    LeaveType annual = LeaveType.builder().code("ANNUAL").nameEn("Annual Leave").isActive(1).isPaid(1)
            .requiresDocument(0).minNoticeDays(0).maxConsecutiveDays(0).applicableGender("ALL").build();
    LeaveBalance balance = LeaveBalance.builder().employeeId(EMPLOYEE).leaveTypeCode("ANNUAL")
            .year(LocalDate.now().getYear()).totalDays(21.0).usedDays(0.0).pendingDays(0.0).build();

    // The next Monday, strictly after today, and the Friday of that week.
    LocalDate monday = nextMonday();
    LocalDate friday = monday.plusDays(4);

    static LocalDate nextMonday() {
        LocalDate d = LocalDate.now().plusDays(1);
        while (d.getDayOfWeek() != DayOfWeek.MONDAY) d = d.plusDays(1);
        return d;
    }

    @BeforeEach
    void setUp() {
        service = new LeaveServiceImpl(requests, balances, types, employees, new WorkCalendar(holidays), jobDetails, shifts, guard, storage, contents, mock(AuditTrail.class));
        when(employees.findById(EMPLOYEE)).thenReturn(Optional.of(
                Employee.builder().id(EMPLOYEE).employeeCode("EMP-5").firstName("Sara").lastName("Test")
                        .gender(Gender.FEMALE).build()));
        when(types.findByCodeIgnoreCase("ANNUAL")).thenReturn(Optional.of(annual));
        when(employees.existsById(EMPLOYEE)).thenReturn(true);
        givenBalanceFor(monday);
        when(requests.saveAndFlush(any(LeaveRequest.class))).thenAnswer(i -> {
            LeaveRequest r = i.getArgument(0);
            r.setLeaveReqId(501L);
            return r;
        });
        when(requests.save(any(LeaveRequest.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void givenBalanceFor(LocalDate start) {
        balance.setYear(start.getYear());
        when(balances.findByEmployeeIdAndLeaveTypeCodeAndYear(EMPLOYEE, "ANNUAL", start.getYear()))
                .thenReturn(Optional.of(balance));
    }

    private static BusinessRuleException refused(org.junit.jupiter.api.function.Executable call) {
        return assertThrows(BusinessRuleException.class, call);
    }

    private CreateLeaveRequest apply(LocalDate from, LocalDate to) {
        return CreateLeaveRequest.builder().leaveTypeCode("ANNUAL").startDate(from).endDate(to)
                .reason("Family visit").build();
    }

    private LeaveRequestResponse applyNoFile(LocalDate from, LocalDate to) {
        return service.applyLeave(EMPLOYEE, apply(from, to), null);
    }

    private void assertNothingSaved() {
        verify(requests, never()).saveAndFlush(any());
        verify(balances, never()).save(any());
    }

    // ── Applying: counting the days ──────────────────────────

    @Test
    void aWorkingWeekIsFiveDaysAndGoesIntoPending() {
        LeaveRequestResponse r = applyNoFile(monday, friday);

        assertEquals(5.0, r.getTotalDays());
        assertEquals("PENDING", r.getStatus());
        assertEquals(5.0, balance.getPendingDays());
        assertEquals(16.0, balance.getAvailableDays());
        verify(balances).save(balance);
    }

    @Test
    void saturdayAndSundayAreNotCounted() {
        LeaveRequestResponse r = applyNoFile(friday.minusDays(0), friday.plusDays(3));   // Fri, Sat, Sun, Mon

        assertEquals(2.0, r.getTotalDays());
    }

    @Test
    void aRangeOfOnlyWeekendDaysHasNoWorkingDays() {
        var ex = refused(() -> applyNoFile(friday.plusDays(1), friday.plusDays(2)));

        assertEquals("NO_WORKING_DAYS", ex.getRuleCode());
        assertNothingSaved();
    }

    private void givenShiftWorking(String workingDays) {
        when(jobDetails.findByEmployeeIdAndIsCurrent(EMPLOYEE, 1)).thenReturn(Optional.of(
                EmployeeJobDetails.builder().employeeId(EMPLOYEE).shiftId(9L).build()));
        when(shifts.findById(9L)).thenReturn(Optional.of(WorkShift.builder().workingDays(workingDays).build()));
    }

    @Test
    void theEmployeesShiftDecidesWhichDaysAreFree() {
        givenShiftWorking("SUN,MON,TUE,WED,THU");   // Friday and Saturday off

        // Fri, Sat, Sun, Mon: only Sunday and Monday cost a day
        assertEquals(2.0, applyNoFile(friday, friday.plusDays(3)).getTotalDays());
        assertEquals(0.0, service.countWorkingDays(EMPLOYEE, friday, friday.plusDays(1)).workingDays());
    }

    @Test
    void aShiftThatWorksSaturdayChargesSaturday() {
        givenShiftWorking("MON,TUE,WED,THU,FRI,SAT");

        assertEquals(1.0, service.countWorkingDays(EMPLOYEE, friday.plusDays(1), friday.plusDays(2)).workingDays());
    }

    @Test
    void theWorkingDaysPreviewUsesTheSameRule() {
        assertEquals(5.0, service.countWorkingDays(EMPLOYEE, monday, friday).workingDays());
        assertEquals(2.0, service.countWorkingDays(EMPLOYEE, friday, friday.plusDays(3)).workingDays());
    }

    @Test
    void theWorkingDaysPreviewRejectsAReversedRange() {
        assertEquals("INVALID_DATES", refused(() -> service.countWorkingDays(EMPLOYEE, friday, monday)).getRuleCode());
    }

    @Test
    void aHolidayOnAWorkingDayIsNotCounted() {
        LocalDate wednesday = monday.plusDays(2);
        when(holidays.findHolidaysBetween(monday, friday)).thenReturn(List.of(
                HolidayCalendar.builder().holidayDate(wednesday).build()));

        LeaveRequestResponse r = applyNoFile(monday, friday);

        assertEquals(4.0, r.getTotalDays());
    }

    @Test
    void anOptionalHolidayStillCostsALeaveDay() {
        LocalDate wednesday = monday.plusDays(2);
        when(holidays.findHolidaysBetween(monday, friday)).thenReturn(List.of(
                HolidayCalendar.builder().holidayDate(wednesday).holidayType("OPTIONAL").build()));

        assertEquals(5.0, applyNoFile(monday, friday).getTotalDays());
    }

    // ── Applying: who and what ───────────────────────────────

    @Test
    void anUnknownOrInactiveLeaveTypeIsRefused() {
        when(types.findByCodeIgnoreCase("ANNUAL")).thenReturn(Optional.empty());
        assertEquals("INVALID_LEAVE_TYPE", refused(() -> applyNoFile(monday, friday)).getRuleCode());

        when(types.findByCodeIgnoreCase("ANNUAL")).thenReturn(Optional.of(annual));
        annual.setIsActive(0);
        assertEquals("LEAVE_TYPE_INACTIVE", refused(() -> applyNoFile(monday, friday)).getRuleCode());
        assertNothingSaved();
    }

    @Test
    void aGenderRestrictedTypeIsOnlyForThatGender() {
        annual.setApplicableGender("MALE");
        assertEquals("GENDER_NOT_ELIGIBLE", refused(() -> applyNoFile(monday, friday)).getRuleCode());

        annual.setApplicableGender("FEMALE");
        assertDoesNotThrow(() -> applyNoFile(monday, friday));
    }

    @Test
    void anEmployeeWithNoGenderCannotTakeAGenderRestrictedLeave() {
        when(employees.findById(EMPLOYEE)).thenReturn(Optional.of(
                Employee.builder().id(EMPLOYEE).employeeCode("EMP-5").firstName("Sara").lastName("Test").build()));
        annual.setApplicableGender("FEMALE");

        assertEquals("GENDER_NOT_ELIGIBLE", refused(() -> applyNoFile(monday, friday)).getRuleCode());
    }

    @Test
    void anUnknownEmployeeIsNotFound() {
        when(employees.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.applyLeave(99L, apply(monday, friday), null));
    }

    // ── Applying: dates ──────────────────────────────────────

    @Test
    void theEndCannotBeBeforeTheStartAndThePastCannotBeRequested() {
        assertEquals("INVALID_DATES", refused(() -> applyNoFile(friday, monday)).getRuleCode());

        LocalDate yesterday = LocalDate.now().minusDays(1);
        assertEquals("PAST_DATE", refused(() -> applyNoFile(yesterday, monday)).getRuleCode());
        assertNothingSaved();
    }

    @Test
    void minimumNoticeIsEnforced() {
        annual.setMinNoticeDays(60);

        var ex = refused(() -> applyNoFile(monday, friday));

        assertEquals("INSUFFICIENT_NOTICE", ex.getRuleCode());
        assertTrue(ex.getMessage().contains("60"));
    }

    @Test
    void noticeOfZeroMeansAnyStartDate() {
        annual.setMinNoticeDays(0);

        assertDoesNotThrow(() -> applyNoFile(monday, friday));
    }

    @Test
    void aMaximumRunOfDaysIsEnforcedAndZeroMeansNoLimit() {
        annual.setMaxConsecutiveDays(3);
        assertEquals("EXCEEDS_MAX_CONSECUTIVE", refused(() -> applyNoFile(monday, friday)).getRuleCode());

        annual.setMaxConsecutiveDays(5);
        assertDoesNotThrow(() -> applyNoFile(monday, friday));

        annual.setMaxConsecutiveDays(0);
        assertDoesNotThrow(() -> applyNoFile(monday.plusDays(7), friday.plusDays(7)));
    }

    @Test
    void overlappingAnotherRequestIsRefused() {
        when(requests.findOverlapping(EMPLOYEE, monday, friday)).thenReturn(List.of(new LeaveRequest()));

        var ex = refused(() -> applyNoFile(monday, friday));

        assertEquals("DATE_OVERLAP", ex.getRuleCode());
        assertNothingSaved();
    }

    // ── Applying: balance ────────────────────────────────────

    @Test
    void withoutABalanceRowForTheYearTheRequestIsRefused() {
        when(balances.findByEmployeeIdAndLeaveTypeCodeAndYear(EMPLOYEE, "ANNUAL", monday.getYear()))
                .thenReturn(Optional.empty());

        assertEquals("NO_BALANCE", refused(() -> applyNoFile(monday, friday)).getRuleCode());
    }

    @Test
    void whatIsAlreadyUsedOrPendingCannotBeRequestedAgain() {
        balance.setTotalDays(10.0);
        balance.setUsedDays(2.0);
        balance.setPendingDays(4.0);          // 4 available

        var ex = refused(() -> applyNoFile(monday, friday));   // 5 days wanted
        assertEquals("INSUFFICIENT_BALANCE", ex.getRuleCode());
        assertEquals(4.0, balance.getAvailableDays());
        assertNothingSaved();

        assertDoesNotThrow(() -> applyNoFile(monday, friday.minusDays(1)));   // exactly 4 days
        assertEquals(0.0, balance.getAvailableDays());
    }

    // ── Applying: documents ──────────────────────────────────

    private MultipartFile file(boolean empty) {
        MultipartFile f = mock(MultipartFile.class);
        when(f.isEmpty()).thenReturn(empty);
        return f;
    }

    @Test
    void aTypeThatNeedsADocumentRefusesARequestWithoutOne() {
        annual.setRequiresDocument(1);

        assertEquals("DOCUMENT_REQUIRED", refused(() -> service.applyLeave(EMPLOYEE, apply(monday, friday), null)).getRuleCode());
        assertEquals("DOCUMENT_REQUIRED",
                refused(() -> service.applyLeave(EMPLOYEE, apply(monday, friday), file(true))).getRuleCode());
        assertNothingSaved();
    }

    @Test
    void aSuppliedDocumentIsStoredWithTheRequest() {
        annual.setRequiresDocument(1);
        MultipartFile pdf = file(false);
        when(storage.prepare(pdf, annual)).thenReturn(
                new LeaveAttachmentStorage.Prepared(new byte[] {1, 2, 3}, "note.pdf", 3L));

        LeaveRequestResponse r = service.applyLeave(EMPLOYEE, apply(monday, friday), pdf);

        assertTrue(r.getHasAttachment());
        assertEquals("note.pdf", r.getAttachmentName());
        verify(contents).save(any(LeaveRequestAttachmentContent.class));
    }

    @Test
    void theDocumentIsOnlyCheckedAfterTheBusinessRulesPass() {
        MultipartFile pdf = file(false);
        annual.setMaxConsecutiveDays(1);

        refused(() -> service.applyLeave(EMPLOYEE, apply(monday, friday), pdf));

        verifyNoInteractions(storage);
    }

    // ── Cancelling ───────────────────────────────────────────

    private LeaveRequest pendingRequest(double days) {
        LeaveRequest r = new LeaveRequest();
        r.setLeaveReqId(501L);
        r.setEmployeeId(EMPLOYEE);
        r.setLeaveTypeCode("ANNUAL");
        r.setStartDate(monday);
        r.setEndDate(friday);
        r.setTotalDays(days);
        r.setStatus(LeaveStatus.PENDING);
        when(requests.findById(501L)).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void cancellingAPendingRequestGivesTheDaysBack() {
        balance.setPendingDays(5.0);
        LeaveRequest r = pendingRequest(5.0);

        service.cancelLeave(501L, EMPLOYEE);

        assertEquals(LeaveStatus.CANCELLED, r.getStatus());
        assertEquals(0.0, balance.getPendingDays());
    }

    @Test
    void pendingDaysNeverGoBelowZero() {
        balance.setPendingDays(2.0);          // less than the request: data already off
        pendingRequest(5.0);

        service.cancelLeave(501L, EMPLOYEE);

        assertEquals(0.0, balance.getPendingDays());
    }

    @Test
    void youCannotCancelSomeoneElsesRequestOrOneThatIsNotPending() {
        LeaveRequest r = pendingRequest(5.0);

        assertEquals("UNAUTHORIZED", refused(() -> service.cancelLeave(501L, 77L)).getRuleCode());
        assertEquals(LeaveStatus.PENDING, r.getStatus());

        r.setStatus(LeaveStatus.APPROVED);
        assertEquals("INVALID_STATUS", refused(() -> service.cancelLeave(501L, EMPLOYEE)).getRuleCode());
        verify(balances, never()).save(any());
    }

    // ── Approving and rejecting ──────────────────────────────

    private static ApproveLeaveRequest decision(String action, String remarks) {
        return ApproveLeaveRequest.builder().action(action).remarks(remarks).build();
    }

    @Test
    void approvingMovesTheDaysFromPendingToUsed() {
        balance.setPendingDays(5.0);
        LeaveRequest r = pendingRequest(5.0);

        LeaveRequestResponse result = service.processLeave(501L, decision("APPROVED", null), "hr.admin");

        assertEquals("APPROVED", result.getStatus());
        assertEquals(0.0, balance.getPendingDays());
        assertEquals(5.0, balance.getUsedDays());
        assertEquals("hr.admin", r.getApprovedBy());
        assertNotNull(r.getApprovedAt());
    }

    @Test
    void rejectingReleasesTheDaysAndKeepsTheReason() {
        balance.setPendingDays(5.0);
        LeaveRequest r = pendingRequest(5.0);

        service.processLeave(501L, decision("REJECTED", "Busy period"), "hr.admin");

        assertEquals(LeaveStatus.REJECTED, r.getStatus());
        assertEquals("Busy period", r.getRejectionReason());
        assertEquals(0.0, balance.getPendingDays());
        assertEquals(0.0, balance.getUsedDays());
        assertEquals(21.0, balance.getAvailableDays());
    }

    @Test
    void nobodyCanDecideTheirOwnRequest() {
        balance.setPendingDays(5.0);
        LeaveRequest r = pendingRequest(5.0);
        when(guard.currentEmployeeId()).thenReturn(EMPLOYEE);

        assertEquals("SELF_APPROVAL",
                refused(() -> service.processLeave(501L, decision("APPROVED", null), "hr.admin")).getRuleCode());
        assertEquals(LeaveStatus.PENDING, r.getStatus());
        assertEquals(5.0, balance.getPendingDays());
        verify(balances, never()).save(any());
    }

    @Test
    void anHrAdminMayDecideTheirOwnRequest() {
        balance.setPendingDays(5.0);
        LeaveRequest r = pendingRequest(5.0);
        when(guard.currentEmployeeId()).thenReturn(EMPLOYEE);
        when(guard.isHrAdmin()).thenReturn(true);

        service.processLeave(501L, decision("APPROVED", null), "hr.admin");

        assertEquals(LeaveStatus.APPROVED, r.getStatus());
    }

    @Test
    void onlyAPendingRequestCanBeProcessed() {
        LeaveRequest r = pendingRequest(5.0);
        r.setStatus(LeaveStatus.APPROVED);

        assertEquals("INVALID_STATUS",
                refused(() -> service.processLeave(501L, decision("APPROVED", null), "hr.admin")).getRuleCode());
        verify(balances, never()).save(any());
    }

    @Test
    void aMissingRequestOrBalanceIsNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> service.processLeave(999L, decision("APPROVED", null), "hr.admin"));

        pendingRequest(5.0);
        when(balances.findByEmployeeIdAndLeaveTypeCodeAndYear(EMPLOYEE, "ANNUAL", monday.getYear()))
                .thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.processLeave(501L, decision("APPROVED", null), "hr.admin"));
    }

    // ── Reading ──────────────────────────────────────────────

    @Test
    void aRequestBelongingToAnotherEmployeeLooksLikeItDoesNotExist() {
        pendingRequest(5.0);

        assertThrows(ResourceNotFoundException.class, () -> service.getLeaveById(77L, 501L));
        assertThrows(ResourceNotFoundException.class, () -> service.getAttachment(77L, 501L));
    }

    @Test
    void aRequestWithoutAnAttachmentHasNothingToDownload() {
        pendingRequest(5.0);

        assertThrows(ResourceNotFoundException.class, () -> service.getAttachment(EMPLOYEE, 501L));
    }

    @Test
    void theBalanceSummaryHidesTypesTheEmployeesGenderCannotTake() {
        LeaveType maternity = LeaveType.builder().code("MATERNITY").nameEn("Maternity").applicableGender("FEMALE").build();
        when(types.findByCodeIgnoreCase("MATERNITY")).thenReturn(Optional.of(maternity));
        LeaveBalance maternityRow = LeaveBalance.builder().employeeId(EMPLOYEE).leaveTypeCode("MATERNITY").year(2027)
                .totalDays(90.0).usedDays(0.0).pendingDays(0.0).build();
        when(balances.findByEmployeeIdAndYear(EMPLOYEE, 2027)).thenReturn(List.of(balance, maternityRow));

        // The employee in setUp is a woman: both are listed.
        assertEquals(2, service.getBalances(EMPLOYEE, 2027).size());

        when(employees.findById(EMPLOYEE)).thenReturn(Optional.of(
                Employee.builder().id(EMPLOYEE).employeeCode("EMP-5").firstName("Omar").lastName("Test").gender(Gender.MALE).build()));
        List<LeaveBalanceResponse> forAMan = service.getBalances(EMPLOYEE, 2027);

        assertEquals(List.of("ANNUAL"), forAMan.stream().map(LeaveBalanceResponse::getLeaveType).toList());

        maternityRow.setUsedDays(2.0);   // days already taken are never hidden
        assertEquals(2, service.getBalances(EMPLOYEE, 2027).size());
    }

    @Test
    void theListIsFilteredByStatusWhenAskedAndOtherwiseUnfiltered() {
        when(employees.existsById(EMPLOYEE)).thenReturn(true);
        when(requests.findByEmployeeIdAndStatusOrderByCreatedAtDesc(eq(EMPLOYEE), eq(LeaveStatus.PENDING), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(requests.findByEmployeeIdOrderByCreatedAtDesc(eq(EMPLOYEE), any())).thenReturn(new PageImpl<>(List.of()));

        service.getLeavesByEmployee(EMPLOYEE, LeaveFilterRequest.builder().status("PENDING").build());
        verify(requests).findByEmployeeIdAndStatusOrderByCreatedAtDesc(eq(EMPLOYEE), eq(LeaveStatus.PENDING), any());

        service.getLeavesByEmployee(EMPLOYEE, LeaveFilterRequest.builder().build());
        verify(requests).findByEmployeeIdOrderByCreatedAtDesc(eq(EMPLOYEE), any());
    }
}
