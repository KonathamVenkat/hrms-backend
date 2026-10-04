package com.hrms.attendance.service.impl;

import com.hrms.attendance.dto.request.CheckInRequest;
import com.hrms.attendance.dto.request.CheckOutRequest;
import com.hrms.attendance.entity.AttendanceLog;
import com.hrms.attendance.enums.AttendanceStatus;
import com.hrms.attendance.repository.AttendanceLogRepository;
import com.hrms.attendance.repository.OvertimeRequestRepository;
import com.hrms.attendance.service.AttendanceSummaryService;
import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.entity.EmployeeJobDetails;
import com.hrms.employee.entity.WorkShift;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.repository.WorkShiftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class AttendanceServiceImplTest {

    AttendanceLogRepository       logs      = mock(AttendanceLogRepository.class);
    EmployeeRepository            employees = mock(EmployeeRepository.class);
    EmployeeJobDetailsRepository  jobs      = mock(EmployeeJobDetailsRepository.class);
    WorkShiftRepository           shifts    = mock(WorkShiftRepository.class);
    OvertimeRequestRepository     ot        = mock(OvertimeRequestRepository.class);
    AttendanceSummaryService      summaries = mock(AttendanceSummaryService.class);
    EmployeeAccessGuard           guard     = mock(EmployeeAccessGuard.class);
    AttendanceDayClassifier       classifier = mock(AttendanceDayClassifier.class);
    AttendanceDayRecorder         recorder  = mock(AttendanceDayRecorder.class);
    AttendanceServiceImpl         service;

    LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        AttendanceCalculator calculator = new AttendanceCalculator(jobs, shifts, ot);
        service = new AttendanceServiceImpl(logs, employees, calculator, summaries, guard, classifier, recorder);

        Employee e = new Employee();
        e.setId(5L);
        e.setEmployeeCode("EMP-5");
        e.setFirstName("Sara");
        e.setLastName("Khan");
        when(employees.findById(5L)).thenReturn(Optional.of(e));
        when(logs.findNextSequenceValue()).thenReturn(700L);
        when(logs.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private void assignShift(boolean overnight, String start, String end) {
        WorkShift shift = WorkShift.builder().shiftId(1L).shiftName("Test").startTime(start).endTime(end)
                .workingHours(new BigDecimal("8")).gracePeriod(0).workingDays("SUN,MON,TUE,WED,THU")
                .isOvernight(overnight ? 1 : 0).build();
        when(jobs.findByEmployeeIdAndIsCurrent(5L, 1))
                .thenReturn(Optional.of(EmployeeJobDetails.builder().shiftId(1L).build()));
        when(shifts.findById(1L)).thenReturn(Optional.of(shift));
    }

    private static AttendanceLog log(LocalDate date, LocalDateTime in, LocalDateTime out, AttendanceStatus status) {
        return AttendanceLog.builder().logId(11L).employeeId(5L).employeeCode("EMP-5")
                .attendanceDate(date).checkInTime(in).checkOutTime(out).status(status).build();
    }

    private static void notNearMidnight() {
        assumeTrue(LocalTime.now().isAfter(LocalTime.of(0, 3)) && LocalTime.now().isBefore(LocalTime.of(23, 56)),
                "the test uses 'now' and must not straddle midnight");
    }

    // ── Check-in ─────────────────────────────────────────────

    @Test
    void checkInCreatesTheDaysRow() {
        notNearMidnight();
        when(logs.findByEmployeeIdAndAttendanceDateAndIsActive(5L, today, 1)).thenReturn(Optional.empty());

        service.checkIn(new CheckInRequest(5L, null, null, null));

        ArgumentCaptor<AttendanceLog> saved = ArgumentCaptor.forClass(AttendanceLog.class);
        verify(logs).save(saved.capture());
        assertEquals(700L, saved.getValue().getLogId());
        assertEquals(today, saved.getValue().getAttendanceDate());
        assertNotNull(saved.getValue().getCheckInTime());
        verify(classifier).assertMayCheckIn(eq5(), any(), eq(today));
    }

    @Test
    void checkInReusesTheRowTheNightlyJobWrote() {
        notNearMidnight();
        AttendanceLog placeholder = log(today, null, null, AttendanceStatus.ABSENT);
        when(logs.findByEmployeeIdAndAttendanceDateAndIsActive(5L, today, 1)).thenReturn(Optional.of(placeholder));

        service.checkIn(new CheckInRequest(5L, null, null, null));

        verify(logs).save(placeholder);
        verify(logs, never()).findNextSequenceValue();
        assertNotNull(placeholder.getCheckInTime());
        assertNotEquals(AttendanceStatus.ABSENT, placeholder.getStatus());
    }

    @Test
    void checkInTwiceIsRefused() {
        notNearMidnight();
        when(logs.findByEmployeeIdAndAttendanceDateAndIsActive(5L, today, 1))
                .thenReturn(Optional.of(log(today, today.atTime(8, 0), null, AttendanceStatus.PRESENT)));

        assertThrows(BusinessRuleException.class, () -> service.checkIn(new CheckInRequest(5L, null, null, null)));
        verify(logs, never()).save(any());
    }

    @Test
    void aWeekendWithoutOvertimeOrLeaveBlocksCheckInAndWritesNothing() {
        notNearMidnight();
        doThrow(new BusinessRuleException("NON_WORKING_DAY", "Today is a weekend."))
                .when(classifier).assertMayCheckIn(anyLong(), any(), any());

        var ex = assertThrows(BusinessRuleException.class,
                () -> service.checkIn(new CheckInRequest(5L, null, null, null)));

        assertEquals("NON_WORKING_DAY", ex.getRuleCode());
        verify(logs, never()).save(any());
    }

    @Test
    void workOnAWeeklyOffIsNeverLateEvenWithAShiftThatStartedHoursAgo() {
        notNearMidnight();
        assignShift(false, "00:00", "08:00");
        when(classifier.isNonWorkingDay(any(), eq(today))).thenReturn(true);
        when(logs.findByEmployeeIdAndAttendanceDateAndIsActive(5L, today, 1)).thenReturn(Optional.empty());

        service.checkIn(new CheckInRequest(5L, null, null, null));

        ArgumentCaptor<AttendanceLog> saved = ArgumentCaptor.forClass(AttendanceLog.class);
        verify(logs).save(saved.capture());
        assertEquals(AttendanceStatus.PRESENT, saved.getValue().getStatus());
        assertEquals(0, saved.getValue().getLateMinutes());
    }

    // ── Check-out ────────────────────────────────────────────

    @Test
    void checkOutOnAWeeklyOffCountsEveryWorkedMinuteAsOvertime() {
        notNearMidnight();
        assignShift(false, "08:00", "16:00");
        LocalDateTime in = LocalDateTime.now().minusMinutes(120);
        AttendanceLog open = log(today, in, null, AttendanceStatus.PRESENT);
        when(logs.findByEmployeeIdAndAttendanceDateAndIsActive(5L, today, 1)).thenReturn(Optional.of(open));
        when(classifier.isNonWorkingDay(any(), eq(today))).thenReturn(true);
        when(ot.sumApprovedMinutes(anyLong(), any())).thenReturn(0L);

        service.checkOut(new CheckOutRequest(5L, null, null));

        assertTrue(open.getWorkingMinutes() >= 119);
        assertEquals(open.getWorkingMinutes(), open.getOvertimeMinutes());
        assertEquals(0, open.getEarlyLeaveMinutes());
        assertEquals(AttendanceStatus.PRESENT, open.getStatus());
    }

    @Test
    void checkOutWithoutACheckInIsRefused() {
        notNearMidnight();
        when(logs.findByEmployeeIdAndAttendanceDateAndIsActive(5L, today, 1))
                .thenReturn(Optional.of(log(today, null, null, AttendanceStatus.ABSENT)));

        assertThrows(BusinessRuleException.class, () -> service.checkOut(new CheckOutRequest(5L, null, null)));
    }

    // ── Overnight shifts ─────────────────────────────────────

    @Test
    void anOvernightShiftCheckOutAfterMidnightClosesYesterdaysLog() {
        notNearMidnight();
        assignShift(true, "22:00", "23:59"); // ends "after" every time of day we can test at
        LocalDate yesterday = today.minusDays(1);
        AttendanceLog open = log(yesterday, LocalDateTime.now().minusHours(6), null, AttendanceStatus.PRESENT);
        when(logs.findByEmployeeIdAndAttendanceDateAndIsActive(5L, yesterday, 1)).thenReturn(Optional.of(open));
        when(ot.sumApprovedMinutes(anyLong(), any())).thenReturn(0L);

        service.checkOut(new CheckOutRequest(5L, null, null));

        assertNotNull(open.getCheckOutTime());
        verify(logs, never()).findByEmployeeIdAndAttendanceDateAndIsActive(5L, today, 1);
    }

    @Test
    void anOvernightShiftPunchBeforeTheShiftEndBelongsToYesterday() {
        notNearMidnight();
        assignShift(true, "22:00", "23:59");
        LocalDate yesterday = today.minusDays(1);
        when(logs.findByEmployeeIdAndAttendanceDateAndIsActive(5L, yesterday, 1)).thenReturn(Optional.empty());

        service.checkIn(new CheckInRequest(5L, null, null, null));

        ArgumentCaptor<AttendanceLog> saved = ArgumentCaptor.forClass(AttendanceLog.class);
        verify(logs).save(saved.capture());
        assertEquals(yesterday, saved.getValue().getAttendanceDate());
    }

    @Test
    void anOvernightShiftEndingAtMidnightKeepsTodayAsTheShiftDay() {
        notNearMidnight();
        assignShift(true, "16:00", "00:00");
        when(logs.findByEmployeeIdAndAttendanceDateAndIsActive(5L, today, 1)).thenReturn(Optional.empty());

        service.checkIn(new CheckInRequest(5L, null, null, null));

        ArgumentCaptor<AttendanceLog> saved = ArgumentCaptor.forClass(AttendanceLog.class);
        verify(logs).save(saved.capture());
        assertEquals(today, saved.getValue().getAttendanceDate());
    }

    @Test
    void todaysLogForAnOvernightWorkerIsTheShiftStillOpenFromYesterday() {
        notNearMidnight();
        assignShift(true, "22:00", "23:59");
        LocalDate yesterday = today.minusDays(1);
        AttendanceLog open = log(yesterday, LocalDateTime.now().minusHours(5), null, AttendanceStatus.PRESENT);
        when(logs.findByEmployeeIdAndAttendanceDateAndIsActive(5L, yesterday, 1)).thenReturn(Optional.of(open));

        var response = service.getTodayLog(5L);

        assertNotNull(response);
        assertEquals(yesterday, response.attendanceDate());
    }

    // ── Regenerating day records ─────────────────────────────

    @Test
    void regenerationRejectsABadRange() {
        LocalDate yesterday = today.minusDays(1);

        assertEquals("INVALID_RANGE", assertThrows(BusinessRuleException.class,
                () -> service.regenerateDayRecords(yesterday, yesterday.minusDays(1))).getRuleCode());
        assertEquals("DAY_NOT_OVER", assertThrows(BusinessRuleException.class,
                () -> service.regenerateDayRecords(yesterday, today)).getRuleCode());
        assertEquals("RANGE_TOO_LONG", assertThrows(BusinessRuleException.class,
                () -> service.regenerateDayRecords(yesterday.minusDays(80), yesterday)).getRuleCode());
        verifyNoInteractions(recorder);
    }

    @Test
    void regenerationRefreshesEachEmployeeAndMonthOnce() {
        LocalDate d2 = today.minusDays(2);
        LocalDate d1 = today.minusDays(1);
        when(recorder.generateFor(any())).thenReturn(new AttendanceDayRecorder.Result(1, 0, Set.of(5L)));
        when(logs.findByAttendanceDateAndIsActive(d2, 1)).thenReturn(List.of(log(d2, null, null, AttendanceStatus.ABSENT)));
        when(logs.findByAttendanceDateAndIsActive(d1, 1)).thenReturn(List.of(log(d1, null, null, AttendanceStatus.ABSENT)));

        var result = service.regenerateDayRecords(d2, d1);

        assertEquals(2, result.created());
        assertEquals(1, result.employeesRefreshed());
        int distinctMonths = d2.getMonth() == d1.getMonth() ? 1 : 2;
        verify(summaries, times(distinctMonths)).recalculateSummary(eq(5L), anyInt(), anyInt());
    }

    private static Long eq5() { return org.mockito.ArgumentMatchers.eq(5L); }
    private static <T> T eq(T value) { return org.mockito.ArgumentMatchers.eq(value); }
}
