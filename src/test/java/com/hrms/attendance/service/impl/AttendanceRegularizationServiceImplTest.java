package com.hrms.attendance.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.attendance.dto.request.RegularizationActionRequest;
import com.hrms.attendance.dto.request.RegularizationRequest;
import com.hrms.attendance.dto.response.RegularizationResponse;
import com.hrms.attendance.entity.AttendanceLog;
import com.hrms.attendance.entity.AttendanceRegularization;
import com.hrms.attendance.enums.AttendanceStatus;
import com.hrms.attendance.enums.RegularizationStatus;
import com.hrms.attendance.repository.AttendanceLogRepository;
import com.hrms.attendance.repository.AttendanceRegularizationRepository;
import com.hrms.attendance.service.AttendanceSummaryService;
import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.entity.WorkShift;
import com.hrms.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Regularization rules: what may be requested, who may decide, and how an approval corrects the day. */
class AttendanceRegularizationServiceImplTest {

    static final long EMPLOYEE = 5L;
    static final long REVIEWER = 9L;

    AttendanceRegularizationRepository regRepo = mock(AttendanceRegularizationRepository.class);
    AttendanceLogRepository            logRepo = mock(AttendanceLogRepository.class);
    EmployeeRepository                 employeeRepo = mock(EmployeeRepository.class);
    EmployeeAccessGuard                guard = mock(EmployeeAccessGuard.class);
    AttendanceCalculator               calculator = mock(AttendanceCalculator.class);
    AttendanceDayClassifier            classifier = mock(AttendanceDayClassifier.class);
    AttendanceSummaryService           summary = mock(AttendanceSummaryService.class);
    AttendanceRegularizationServiceImpl service;

    WorkShift shift = WorkShift.builder().startTime("08:00").endTime("17:00").build();
    LocalDate yesterday = LocalDate.now().minusDays(1);

    @BeforeEach
    void setUp() {
        service = new AttendanceRegularizationServiceImpl(
                regRepo, logRepo, employeeRepo, guard, calculator, classifier, summary, mock(AuditTrail.class));
        when(employeeRepo.findById(EMPLOYEE)).thenReturn(Optional.of(person(EMPLOYEE, "Sara")));
        when(employeeRepo.findById(REVIEWER)).thenReturn(Optional.of(person(REVIEWER, "Omar")));
        when(calculator.resolveShift(EMPLOYEE)).thenReturn(shift);
        when(regRepo.findNextSequenceValue()).thenReturn(41L);
        when(logRepo.findNextSequenceValue()).thenReturn(900L);
        when(regRepo.save(any(AttendanceRegularization.class))).thenAnswer(i -> i.getArgument(0));
        when(logRepo.save(any(AttendanceLog.class))).thenAnswer(i -> i.getArgument(0));
        when(guard.currentEmployeeId()).thenReturn(REVIEWER);
    }

    private static Employee person(long id, String first) {
        return Employee.builder().id(id).employeeCode("EMP-" + id).firstName(first).lastName("Test").build();
    }

    private static BusinessRuleException refused(org.junit.jupiter.api.function.Executable call) {
        return assertThrows(BusinessRuleException.class, call);
    }

    private RegularizationRequest request(LocalDate date, LocalDateTime in, LocalDateTime out) {
        return new RegularizationRequest(EMPLOYEE, date.toString(), in.toString(),
                out == null ? null : out.toString(), "Forgot to punch at the gate");
    }

    private RegularizationRequest normalDay(LocalDate date) {
        return request(date, date.atTime(8, 0), date.atTime(17, 0));
    }

    private AttendanceLog logWithStatus(AttendanceStatus status, LocalDate date) {
        AttendanceLog l = AttendanceLog.builder().logId(77L).employeeId(EMPLOYEE).attendanceDate(date).status(status).build();
        when(logRepo.findByEmployeeIdAndAttendanceDateAndIsActive(EMPLOYEE, date, 1)).thenReturn(Optional.of(l));
        return l;
    }

    // ── Submitting ───────────────────────────────────────────

    @Test
    void aNormalRequestIsSavedPendingAndLinkedToTheExistingLog() {
        logWithStatus(AttendanceStatus.ABSENT, yesterday);

        RegularizationResponse saved = service.submit(normalDay(yesterday));

        assertEquals(RegularizationStatus.PENDING, saved.status());
        assertEquals(41L, saved.regId());
        assertEquals(77L, saved.logId());
    }

    @Test
    void withoutAnExistingLogTheRequestHasNoLogYet() {
        RegularizationResponse saved = service.submit(normalDay(yesterday));

        assertNull(saved.logId());
    }

    @Test
    void futureDatesAreRefused() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        refused(() -> service.submit(normalDay(tomorrow)));
    }

    @Test
    void onlyTheLastThirtyDaysCanBeRegularized() {
        LocalDate thirtyAgo = LocalDate.now().minusDays(30);
        assertDoesNotThrow(() -> service.submit(normalDay(thirtyAgo)));

        LocalDate thirtyOneAgo = LocalDate.now().minusDays(31);
        refused(() -> service.submit(normalDay(thirtyOneAgo)));
    }

    @Test
    void aSecondPendingRequestForTheSameDayIsRefused() {
        when(regRepo.existsByEmployeeIdAndAttendanceDateAndStatusAndIsActive(
                EMPLOYEE, yesterday, RegularizationStatus.PENDING, 1)).thenReturn(true);

        refused(() -> service.submit(normalDay(yesterday)));
        verify(regRepo, never()).save(any());
    }

    @Test
    void checkOutMustBeAfterCheckIn() {
        refused(() -> service.submit(request(yesterday, yesterday.atTime(17, 0), yesterday.atTime(8, 0))));
        refused(() -> service.submit(request(yesterday, yesterday.atTime(8, 0), yesterday.atTime(8, 0))));
    }

    @Test
    void theCheckOutIsOptional() {
        RegularizationResponse saved = service.submit(request(yesterday, yesterday.atTime(8, 0), null));

        assertNull(saved.requestedOutTime());
    }

    @Test
    void theTimesMustBelongToTheDateAndMayEndNextDayForAnOvernightShift() {
        LocalDate day = LocalDate.now().minusDays(3);

        refused(() -> service.submit(request(day, day.minusDays(1).atTime(22, 0), day.atTime(6, 0))));
        refused(() -> service.submit(request(day, day.atTime(22, 0), day.plusDays(2).atTime(6, 0))));
        assertDoesNotThrow(() -> service.submit(request(day, day.atTime(22, 0), day.plusDays(1).atTime(6, 0))));
    }

    @Test
    void timesInTheFutureAreRefused() {
        // Checked in yesterday, "checked out" at 23:59 today: later than now except in the last minute of the day.
        LocalDateTime tonight = LocalDate.now().atTime(23, 59, 59);

        refused(() -> service.submit(request(yesterday, yesterday.atTime(8, 0), tonight)));
    }

    @Test
    void aLeaveDayCannotBeRegularized() {
        logWithStatus(AttendanceStatus.ON_LEAVE, yesterday);

        var ex = refused(() -> service.submit(normalDay(yesterday)));

        assertTrue(ex.getMessage().contains("ON_LEAVE"), ex.getMessage());
        verify(regRepo, never()).save(any());
    }

    @Test
    void aWeekendOrHolidayDayCanBeRegularizedWhenTheClassifierAllowsIt() {
        for (AttendanceStatus status : new AttendanceStatus[] {AttendanceStatus.WEEKEND, AttendanceStatus.HOLIDAY}) {
            logWithStatus(status, yesterday);

            assertDoesNotThrow(() -> service.submit(normalDay(yesterday)), status.name());
        }
        verify(classifier, atLeastOnce()).assertMayRegularize(EMPLOYEE, shift, yesterday);
    }

    @Test
    void aWeekendOrHolidayWithoutApprovedOvertimeIsRefusedAtSubmitAndAtApproval() {
        doThrow(new BusinessRuleException("NON_WORKING_DAY", "no approved overtime"))
                .when(classifier).assertMayRegularize(EMPLOYEE, shift, yesterday);

        var submit = refused(() -> service.submit(normalDay(yesterday)));
        assertEquals("NON_WORKING_DAY", submit.getRuleCode());
        verify(regRepo, never()).save(any());

        // A request filed earlier, before the rule or before the overtime was cancelled, is checked again.
        pendingNormalDay();
        refused(() -> service.approve(41L, APPROVE));
        verify(logRepo, never()).save(any());
    }

    @Test
    void absentAndPresentDaysCanBeRegularized() {
        for (AttendanceStatus status : new AttendanceStatus[] {AttendanceStatus.ABSENT, AttendanceStatus.PRESENT}) {
            logWithStatus(status, yesterday);

            assertDoesNotThrow(() -> service.submit(normalDay(yesterday)), status.name());
        }
    }

    // ── Approving ────────────────────────────────────────────

    private AttendanceRegularization pending(LocalDate day, LocalDateTime in, LocalDateTime out) {
        AttendanceRegularization r = AttendanceRegularization.builder()
                .regId(41L).employeeId(EMPLOYEE).employeeCode("EMP-" + EMPLOYEE).attendanceDate(day)
                .requestedInTime(in).requestedOutTime(out).reason("Forgot to punch at the gate")
                .status(RegularizationStatus.PENDING).build();
        when(regRepo.findById(41L)).thenReturn(Optional.of(r));
        return r;
    }

    private AttendanceRegularization pendingNormalDay() {
        return pending(yesterday, yesterday.atTime(8, 0), yesterday.atTime(17, 0));
    }

    private static final RegularizationActionRequest APPROVE =
            new RegularizationActionRequest(RegularizationStatus.APPROVED, null);

    @Test
    void approvingRecordsTheLoggedInReviewerAndCorrectsTheDay() {
        AttendanceRegularization reg = pendingNormalDay();
        AttendanceLog existing = logWithStatus(AttendanceStatus.ABSENT, yesterday);

        service.approve(41L, APPROVE);

        assertEquals(RegularizationStatus.APPROVED, reg.getStatus());
        assertEquals(REVIEWER, reg.getReviewedBy());
        assertEquals(1, existing.getIsRegularized());
        verify(calculator).applyCheckIn(existing, yesterday.atTime(8, 0), shift, false);
        verify(calculator).applyCheckOut(existing, yesterday.atTime(17, 0), shift, false);
        assertEquals(77L, reg.getLogId());
    }

    @Test
    void whenThereIsNoLogApprovalCreatesOne() {
        AttendanceRegularization reg = pendingNormalDay();

        service.approve(41L, APPROVE);

        ArgumentCaptor<AttendanceLog> saved = ArgumentCaptor.forClass(AttendanceLog.class);
        verify(logRepo).save(saved.capture());
        assertEquals(900L, saved.getValue().getLogId());
        assertEquals(EMPLOYEE, saved.getValue().getEmployeeId());
        assertEquals(yesterday, saved.getValue().getAttendanceDate());
        assertEquals(1, saved.getValue().getIsRegularized());
        assertEquals(900L, reg.getLogId());
    }

    @Test
    void theDaysWorkingStatusDecidesHowTheTimesAreScored() {
        pendingNormalDay();
        when(classifier.isNonWorkingDay(shift, yesterday)).thenReturn(true);

        service.approve(41L, APPROVE);

        verify(calculator).applyCheckIn(any(), eq(yesterday.atTime(8, 0)), eq(shift), eq(true));
        verify(calculator).applyCheckOut(any(), eq(yesterday.atTime(17, 0)), eq(shift), eq(true));
    }

    @Test
    void aCheckInOnlyRequestKeepsTheExistingCheckOut() {
        pending(yesterday, yesterday.atTime(8, 0), null);
        AttendanceLog existing = logWithStatus(AttendanceStatus.PRESENT, yesterday);
        existing.setCheckOutTime(yesterday.atTime(16, 30));

        service.approve(41L, APPROVE);

        verify(calculator).applyCheckOut(existing, yesterday.atTime(16, 30), shift, false);
    }

    @Test
    void aCheckInOnlyRequestForADayWithNoCheckOutLeavesItOpen() {
        pending(yesterday, yesterday.atTime(8, 0), null);
        logWithStatus(AttendanceStatus.ABSENT, yesterday);

        service.approve(41L, APPROVE);

        verify(calculator, never()).applyCheckOut(any(), any(), any(), anyBoolean());
    }

    @Test
    void aNewCheckInAfterTheExistingCheckOutAsksForACheckOutToo() {
        pending(yesterday, yesterday.atTime(18, 0), null);
        AttendanceLog existing = logWithStatus(AttendanceStatus.PRESENT, yesterday);
        existing.setCheckOutTime(yesterday.atTime(16, 30));

        var ex = refused(() -> service.approve(41L, APPROVE));

        assertTrue(ex.getMessage().contains("check-out"));
        verify(logRepo, never()).save(any());
    }

    @Test
    void aDayMarkedLeaveSinceTheRequestWasMadeCannotBeApproved() {
        AttendanceRegularization reg = pendingNormalDay();
        logWithStatus(AttendanceStatus.ON_LEAVE, yesterday);

        refused(() -> service.approve(41L, APPROVE));

        verify(logRepo, never()).save(any());
        verify(calculator, never()).applyCheckIn(any(), any(), any(), anyBoolean());
    }

    @Test
    void approvingAPastDayRefreshesThatMonthsSummaryButTodayDoesNot() {
        pendingNormalDay();
        service.approve(41L, APPROVE);
        verify(summary).recalculateSummary(EMPLOYEE, yesterday.getYear(), yesterday.getMonthValue());

        reset(summary);
        LocalDate today = LocalDate.now();
        pending(today, today.atTime(0, 5), today.atTime(0, 10));
        service.approve(41L, APPROVE);
        verifyNoInteractions(summary);
    }

    // ── Who may decide ───────────────────────────────────────

    @Test
    void nobodyCanApproveOrRejectTheirOwnRequest() {
        pendingNormalDay();
        when(guard.currentEmployeeId()).thenReturn(EMPLOYEE);

        var approve = refused(() -> service.approve(41L, APPROVE));
        var reject = refused(() -> service.reject(41L,
                new RegularizationActionRequest(RegularizationStatus.REJECTED, "No proof")));

        assertEquals("SELF_APPROVAL", approve.getRuleCode());
        assertEquals("SELF_APPROVAL", reject.getRuleCode());
    }

    @Test
    void anAccountWithoutAnEmployeeRecordCannotReview() {
        pendingNormalDay();
        when(guard.currentEmployeeId()).thenReturn(null);

        var ex = refused(() -> service.approve(41L, APPROVE));

        assertEquals("NO_EMPLOYEE_LINK", ex.getRuleCode());
    }

    @Test
    void onlyAPendingRequestCanBeApprovedOrRejected() {
        AttendanceRegularization reg = pendingNormalDay();
        reg.setStatus(RegularizationStatus.REJECTED);

        refused(() -> service.approve(41L, APPROVE));
        refused(() -> service.reject(41L, new RegularizationActionRequest(RegularizationStatus.REJECTED, "Again")));
    }

    // ── Rejecting and cancelling ─────────────────────────────

    @Test
    void rejectingNeedsAReasonAndLeavesTheDayAlone() {
        AttendanceRegularization reg = pendingNormalDay();

        refused(() -> service.reject(41L, new RegularizationActionRequest(RegularizationStatus.REJECTED, null)));
        refused(() -> service.reject(41L, new RegularizationActionRequest(RegularizationStatus.REJECTED, " ")));
        assertEquals(RegularizationStatus.PENDING, reg.getStatus());

        service.reject(41L, new RegularizationActionRequest(RegularizationStatus.REJECTED, "No proof of presence"));

        assertEquals(RegularizationStatus.REJECTED, reg.getStatus());
        assertEquals("No proof of presence", reg.getRejectionReason());
        verify(logRepo, never()).save(any());
    }

    @Test
    void youCanCancelOnlyYourOwnPendingRequest() {
        AttendanceRegularization reg = pendingNormalDay();

        refused(() -> service.cancel(41L, REVIEWER));   // someone else's request
        assertEquals(RegularizationStatus.PENDING, reg.getStatus());

        service.cancel(41L, EMPLOYEE);
        assertEquals(RegularizationStatus.CANCELLED, reg.getStatus());

        refused(() -> service.cancel(41L, EMPLOYEE));   // no longer pending
    }
}
