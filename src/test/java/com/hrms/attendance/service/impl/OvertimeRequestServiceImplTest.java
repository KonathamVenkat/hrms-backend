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
import com.hrms.attendance.service.AttendanceSummaryService;
import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.entity.WorkShift;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.leave.repository.HolidayCalendarRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Overtime rules: what may be submitted, and who may approve, reject or cancel. */
class OvertimeRequestServiceImplTest {

    static final long EMPLOYEE = 5L;
    static final long REVIEWER = 9L;

    OvertimeRequestRepository otRepo      = mock(OvertimeRequestRepository.class);
    AttendanceLogRepository   logRepo     = mock(AttendanceLogRepository.class);
    EmployeeRepository        employeeRepo = mock(EmployeeRepository.class);
    EmployeeAccessGuard       guard       = mock(EmployeeAccessGuard.class);
    AttendanceCalculator      calculator  = mock(AttendanceCalculator.class);
    AttendanceSummaryService  summary     = mock(AttendanceSummaryService.class);
    HolidayCalendarRepository holidays    = mock(HolidayCalendarRepository.class);
    OvertimeRequestServiceImpl service;

    // Regular shift 08:00-17:00 unless a test says otherwise.
    WorkShift shift = WorkShift.builder().startTime("08:00").endTime("17:00").build();

    @BeforeEach
    void setUp() {
        service = new OvertimeRequestServiceImpl(otRepo, logRepo, employeeRepo, guard, calculator, summary, holidays);
        when(employeeRepo.findById(EMPLOYEE)).thenReturn(Optional.of(person(EMPLOYEE, "Sara")));
        when(employeeRepo.findById(REVIEWER)).thenReturn(Optional.of(person(REVIEWER, "Omar")));
        when(calculator.resolveShift(EMPLOYEE)).thenReturn(shift);
        when(otRepo.findNextSequenceValue()).thenReturn(7L);
        when(otRepo.save(any(OvertimeRequest.class))).thenAnswer(i -> i.getArgument(0));
        when(otRepo.saveAndFlush(any(OvertimeRequest.class))).thenAnswer(i -> i.getArgument(0));
        when(guard.currentEmployeeId()).thenReturn(REVIEWER);
    }

    private static Employee person(long id, String first) {
        return Employee.builder().id(id).employeeCode("EMP-" + id).firstName(first).lastName("Test").build();
    }

    /** The latest Monday-Friday before today, so "post-facto" dates are in the past and inside 30 days. */
    private static LocalDate lastWeekday() {
        LocalDate d = LocalDate.now().minusDays(1);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) d = d.minusDays(1);
        return d;
    }

    private static LocalDate lastSaturday() {
        LocalDate d = LocalDate.now().minusDays(1);
        while (d.getDayOfWeek() != DayOfWeek.SATURDAY) d = d.minusDays(1);
        return d;
    }

    private static OvertimeSubmitRequest submitting(LocalDate date, OvertimeType type, LocalDateTime from, LocalDateTime to) {
        return new OvertimeSubmitRequest(EMPLOYEE, date.toString(), type, from.toString(), to.toString(),
                "Month-end close work", null);
    }

    /** 18:00-20:00 on a weekday: after the shift, 120 minutes. */
    private OvertimeSubmitRequest eveningTwoHours(LocalDate date) {
        return submitting(date, OvertimeType.POST_FACTO, date.atTime(18, 0), date.atTime(20, 0));
    }

    private static BusinessRuleException refused(org.junit.jupiter.api.function.Executable call) {
        return assertThrows(BusinessRuleException.class, call);
    }

    // ── Submitting ───────────────────────────────────────────

    @Test
    void anEveningRequestAfterTheShiftIsSavedAsPending() {
        LocalDate day = lastWeekday();

        OvertimeResponse saved = service.submit(eveningTwoHours(day));

        assertEquals(RegularizationStatus.PENDING, saved.status());
        assertEquals(120, saved.durationMinutes());
        assertEquals("OT-" + LocalDate.now().getYear() + "-000007", saved.otId());
    }

    @Test
    void postFactoOvertimeCannotBeForAFutureDateButPreApprovedCan() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        refused(() -> service.submit(submitting(tomorrow, OvertimeType.POST_FACTO,
                tomorrow.atTime(18, 0), tomorrow.atTime(20, 0))));

        assertDoesNotThrow(() -> service.submit(submitting(tomorrow, OvertimeType.PRE_APPROVED,
                tomorrow.atTime(18, 0), tomorrow.atTime(20, 0))));
    }

    @Test
    void aRequestOlderThanThirtyDaysIsRefused() {
        LocalDate old = LocalDate.now().minusDays(31);

        refused(() -> service.submit(eveningTwoHours(old)));
        verify(otRepo, never()).save(any());
    }

    @Test
    void aSecondPendingRequestForTheSameDayIsRefused() {
        LocalDate day = lastWeekday();
        when(otRepo.existsByEmployeeIdAndOtDateAndStatusAndIsActive(EMPLOYEE, day, RegularizationStatus.PENDING, 1))
                .thenReturn(true);

        refused(() -> service.submit(eveningTwoHours(day)));
    }

    @Test
    void theEndMustBeAfterTheStartAndTheRequestAtLeastThirtyMinutes() {
        LocalDate day = lastWeekday();

        refused(() -> service.submit(submitting(day, OvertimeType.POST_FACTO, day.atTime(20, 0), day.atTime(18, 0))));
        refused(() -> service.submit(submitting(day, OvertimeType.POST_FACTO, day.atTime(18, 0), day.atTime(18, 29))));
        assertDoesNotThrow(() -> service.submit(
                submitting(day, OvertimeType.POST_FACTO, day.atTime(18, 0), day.atTime(18, 30))));
    }

    @Test
    void aDayCannotHoldMoreThanTwelveHoursInTotal() {
        LocalDate day = lastWeekday();
        // 3 hours requested now, 9 already requested: exactly 12 hours is allowed...
        when(otRepo.sumPendingAndApprovedMinutes(EMPLOYEE, day)).thenReturn(9L * 60);
        assertDoesNotThrow(() -> service.submit(
                submitting(day, OvertimeType.POST_FACTO, day.atTime(18, 0), day.atTime(21, 0))));

        // ...one more minute is not.
        when(otRepo.sumPendingAndApprovedMinutes(EMPLOYEE, day)).thenReturn(9L * 60 + 1);
        var ex = refused(() -> service.submit(
                submitting(day, OvertimeType.POST_FACTO, day.atTime(18, 0), day.atTime(21, 0))));
        assertEquals("OT_DAILY_LIMIT", ex.getRuleCode());
    }

    @Test
    void aSingleRequestOverTwelveHoursIsRefused() {
        LocalDate day = lastWeekday();

        var ex = refused(() -> service.submit(submitting(day, OvertimeType.POST_FACTO,
                day.atTime(17, 0), day.plusDays(1).atTime(5, 1))));
        assertEquals("OT_DAILY_LIMIT", ex.getRuleCode());
    }

    @Test
    void theWorkedWindowMustStartOnTheOvertimeDateAndMayEndNextDay() {
        LocalDate day = lastWeekday();

        refused(() -> service.submit(submitting(day, OvertimeType.POST_FACTO,
                day.minusDays(1).atTime(21, 0), day.atTime(1, 0))));
        refused(() -> service.submit(submitting(day, OvertimeType.POST_FACTO,
                day.atTime(23, 0), day.plusDays(2).atTime(0, 30))));
        assertDoesNotThrow(() -> service.submit(submitting(day, OvertimeType.POST_FACTO,
                day.atTime(22, 0), day.plusDays(1).atTime(1, 0))));
    }

    @Test
    void overtimeInsideTheRegularShiftIsRefused() {
        LocalDate day = lastWeekday();

        var ex = refused(() -> service.submit(submitting(day, OvertimeType.POST_FACTO,
                day.atTime(16, 0), day.atTime(18, 0))));
        assertEquals("OT_OVERLAPS_SHIFT", ex.getRuleCode());
        refused(() -> service.submit(submitting(day, OvertimeType.POST_FACTO,
                day.atTime(6, 0), day.atTime(8, 30))));

        // Touching the shift's edges is fine: 06:00-08:00 and 17:00-19:00.
        assertDoesNotThrow(() -> service.submit(submitting(day, OvertimeType.POST_FACTO,
                day.atTime(6, 0), day.atTime(8, 0))));
        assertDoesNotThrow(() -> service.submit(submitting(day, OvertimeType.POST_FACTO,
                day.atTime(17, 0), day.atTime(19, 0))));
    }

    @Test
    void anOvernightShiftBlocksItsWholeSpan() {
        LocalDate day = lastWeekday();
        when(calculator.resolveShift(EMPLOYEE))
                .thenReturn(WorkShift.builder().startTime("22:00").endTime("06:00").build());

        refused(() -> service.submit(submitting(day, OvertimeType.POST_FACTO,
                day.atTime(23, 0), day.plusDays(1).atTime(1, 0))));
        assertDoesNotThrow(() -> service.submit(submitting(day, OvertimeType.POST_FACTO,
                day.atTime(10, 0), day.atTime(12, 0))));
    }

    @Test
    void aWeekendOrHolidayIsAllOvertimeSoTheShiftDoesNotApply() {
        LocalDate saturday = lastSaturday();
        assertDoesNotThrow(() -> service.submit(submitting(saturday, OvertimeType.WEEKEND,
                saturday.atTime(9, 0), saturday.atTime(15, 0))));

        LocalDate holiday = lastWeekday();
        when(holidays.countHolidaysBetween(holiday, holiday)).thenReturn(1L);
        assertDoesNotThrow(() -> service.submit(submitting(holiday, OvertimeType.HOLIDAY,
                holiday.atTime(9, 0), holiday.atTime(15, 0))));
    }

    @Test
    void withoutAShiftAnyWeekdayWindowIsAccepted() {
        LocalDate day = lastWeekday();
        when(calculator.resolveShift(EMPLOYEE)).thenReturn(null);

        assertDoesNotThrow(() -> service.submit(submitting(day, OvertimeType.POST_FACTO,
                day.atTime(10, 0), day.atTime(12, 0))));
    }

    // ── Approving ────────────────────────────────────────────

    private OvertimeRequest pending(LocalDate day) {
        OvertimeRequest r = OvertimeRequest.builder()
                .otId("OT-2026-000001").employeeId(EMPLOYEE).employeeCode("EMP-" + EMPLOYEE)
                .otDate(day).otType(OvertimeType.POST_FACTO)
                .startTime(day.atTime(18, 0)).endTime(day.atTime(20, 0)).durationMinutes(120)
                .reason("Month-end close work").status(RegularizationStatus.PENDING).build();
        when(otRepo.findById("OT-2026-000001")).thenReturn(Optional.of(r));
        return r;
    }

    private static final OvertimeActionRequest APPROVE = new OvertimeActionRequest(RegularizationStatus.APPROVED, null);

    @Test
    void approvingRecordsTheReviewerFromTheLoggedInUser() {
        OvertimeRequest r = pending(lastWeekday());

        OvertimeResponse result = service.approve("OT-2026-000001", APPROVE);

        assertEquals(RegularizationStatus.APPROVED, result.status());
        assertEquals(REVIEWER, r.getReviewedBy());
        assertNotNull(r.getReviewedAt());
    }

    @Test
    void nobodyCanApproveOrRejectTheirOwnRequest() {
        pending(lastWeekday());
        when(guard.currentEmployeeId()).thenReturn(EMPLOYEE);

        var approve = refused(() -> service.approve("OT-2026-000001", APPROVE));
        var reject = refused(() -> service.reject("OT-2026-000001",
                new OvertimeActionRequest(RegularizationStatus.REJECTED, "Not needed")));

        assertEquals("SELF_APPROVAL", approve.getRuleCode());
        assertEquals("SELF_APPROVAL", reject.getRuleCode());
    }

    @Test
    void anAccountWithoutAnEmployeeRecordCannotReview() {
        pending(lastWeekday());
        when(guard.currentEmployeeId()).thenReturn(null);

        var ex = refused(() -> service.approve("OT-2026-000001", APPROVE));

        assertEquals("NO_EMPLOYEE_LINK", ex.getRuleCode());
    }

    @Test
    void onlyAPendingRequestCanBeApprovedOrRejected() {
        OvertimeRequest r = pending(lastWeekday());
        r.setStatus(RegularizationStatus.APPROVED);

        refused(() -> service.approve("OT-2026-000001", APPROVE));
        refused(() -> service.reject("OT-2026-000001",
                new OvertimeActionRequest(RegularizationStatus.REJECTED, "Too late")));
    }

    @Test
    void approvedOvertimeNeverAddsToWhatCheckOutAlreadyDetected() {
        LocalDate day = lastWeekday();
        pending(day);
        AttendanceLog log = AttendanceLog.builder().overtimeMinutes(90).build();
        when(logRepo.findByEmployeeIdAndAttendanceDateAndIsActive(EMPLOYEE, day, 1)).thenReturn(Optional.of(log));
        when(otRepo.sumApprovedMinutes(EMPLOYEE, day)).thenReturn(120L);

        service.approve("OT-2026-000001", APPROVE);

        assertEquals(120, log.getOvertimeMinutes(), "the larger of 90 detected and 120 approved, not 210");
    }

    @Test
    void approvalDoesNotLowerOvertimeThatWasAlreadyHigher() {
        LocalDate day = lastWeekday();
        pending(day);
        AttendanceLog log = AttendanceLog.builder().overtimeMinutes(200).build();
        when(logRepo.findByEmployeeIdAndAttendanceDateAndIsActive(EMPLOYEE, day, 1)).thenReturn(Optional.of(log));
        when(otRepo.sumApprovedMinutes(EMPLOYEE, day)).thenReturn(120L);

        service.approve("OT-2026-000001", APPROVE);

        assertEquals(200, log.getOvertimeMinutes());
        verify(logRepo, never()).save(any());
    }

    @Test
    void withoutADayLogApprovalWritesNothingAndLeavesItToCheckOut() {
        pending(LocalDate.now().plusDays(1));
        when(logRepo.findByEmployeeIdAndAttendanceDateAndIsActive(eq(EMPLOYEE), any(), eq(1)))
                .thenReturn(Optional.empty());

        service.approve("OT-2026-000001", APPROVE);

        verify(logRepo, never()).save(any());
        verifyNoInteractions(summary);
    }

    @Test
    void approvingAPastDayRefreshesThatMonthsSummaryButTodayDoesNot() {
        LocalDate past = lastWeekday();
        pending(past);
        when(logRepo.findByEmployeeIdAndAttendanceDateAndIsActive(EMPLOYEE, past, 1))
                .thenReturn(Optional.of(AttendanceLog.builder().overtimeMinutes(0).build()));
        when(otRepo.sumApprovedMinutes(EMPLOYEE, past)).thenReturn(120L);

        service.approve("OT-2026-000001", APPROVE);

        verify(summary).recalculateSummary(EMPLOYEE, past.getYear(), past.getMonthValue());

        reset(summary);
        LocalDate today = LocalDate.now();
        pending(today);
        when(logRepo.findByEmployeeIdAndAttendanceDateAndIsActive(EMPLOYEE, today, 1))
                .thenReturn(Optional.of(AttendanceLog.builder().overtimeMinutes(0).build()));
        when(otRepo.sumApprovedMinutes(EMPLOYEE, today)).thenReturn(120L);

        service.approve("OT-2026-000001", APPROVE);

        verifyNoInteractions(summary);
    }

    // ── Rejecting ────────────────────────────────────────────

    @Test
    void rejectingNeedsAReasonAndKeepsIt() {
        OvertimeRequest r = pending(lastWeekday());

        refused(() -> service.reject("OT-2026-000001", new OvertimeActionRequest(RegularizationStatus.REJECTED, null)));
        refused(() -> service.reject("OT-2026-000001", new OvertimeActionRequest(RegularizationStatus.REJECTED, "  ")));
        assertEquals(RegularizationStatus.PENDING, r.getStatus());

        service.reject("OT-2026-000001", new OvertimeActionRequest(RegularizationStatus.REJECTED, "Not authorised"));

        assertEquals(RegularizationStatus.REJECTED, r.getStatus());
        assertEquals("Not authorised", r.getRejectionReason());
        verify(logRepo, never()).save(any());
    }

    // ── Cancelling ───────────────────────────────────────────

    @Test
    void youCanCancelOnlyYourOwnPendingRequest() {
        OvertimeRequest r = pending(lastWeekday());

        refused(() -> service.cancel("OT-2026-000001", REVIEWER));   // someone else's request
        assertEquals(RegularizationStatus.PENDING, r.getStatus());

        service.cancel("OT-2026-000001", EMPLOYEE);
        assertEquals(RegularizationStatus.CANCELLED, r.getStatus());

        refused(() -> service.cancel("OT-2026-000001", EMPLOYEE));   // no longer pending
    }
}
