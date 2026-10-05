package com.hrms.attendance.service.impl;

import com.hrms.attendance.dto.response.AttendanceSummaryResponse;
import com.hrms.attendance.entity.AttendanceLog;
import com.hrms.attendance.entity.AttendanceSummary;
import com.hrms.attendance.enums.AttendanceStatus;
import com.hrms.attendance.repository.AttendanceLogRepository;
import com.hrms.attendance.repository.AttendanceSummaryRepository;
import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The monthly summary: how day rows are counted, how today is merged in, and the percentage. */
class AttendanceSummaryServiceImplTest {

    static final long EMPLOYEE = 5L;

    AttendanceSummaryRepository summaryRepo = mock(AttendanceSummaryRepository.class);
    AttendanceLogRepository     logRepo     = mock(AttendanceLogRepository.class);
    EmployeeRepository          employeeRepo = mock(EmployeeRepository.class);
    EmployeeAccessGuard         guard       = mock(EmployeeAccessGuard.class);
    AttendanceDayClassifier     classifier  = mock(AttendanceDayClassifier.class);
    AttendanceSummaryServiceImpl service;

    // A month that is over, so the stored summary is returned as it is.
    static final int YEAR = 2026;
    static final int AUGUST = 8;

    @BeforeEach
    void setUp() {
        service = new AttendanceSummaryServiceImpl(summaryRepo, logRepo, employeeRepo, guard, classifier);
        when(employeeRepo.findById(EMPLOYEE)).thenReturn(Optional.of(
                Employee.builder().id(EMPLOYEE).employeeCode("EMP-5").firstName("Sara").lastName("Test").build()));
        when(summaryRepo.save(any(AttendanceSummary.class))).thenAnswer(i -> i.getArgument(0));
        when(summaryRepo.findNextSequenceValue()).thenReturn(300L);
        when(classifier.halfDayLeaveDates(eq(EMPLOYEE), any(), any())).thenReturn(Set.of());
    }

    private static AttendanceLog day(int dayOfMonth, AttendanceStatus status, Integer work, Integer overtime, Integer late) {
        return AttendanceLog.builder()
                .attendanceDate(LocalDate.of(YEAR, AUGUST, dayOfMonth)).status(status)
                .workingMinutes(work).overtimeMinutes(overtime).lateMinutes(late).build();
    }

    private static AttendanceLog day(int dayOfMonth, AttendanceStatus status) {
        return day(dayOfMonth, status, 0, 0, 0);
    }

    private void augustLogs(AttendanceLog... logs) {
        when(logRepo.findByEmployeeIdAndAttendanceDateBetweenAndIsActiveOrderByAttendanceDateAsc(
                EMPLOYEE, LocalDate.of(YEAR, AUGUST, 1), LocalDate.of(YEAR, AUGUST, 31), 1))
                .thenReturn(List.of(logs));
    }

    // ── Counting the day rows ────────────────────────────────

    @Test
    void everyKindOfDayIsCountedInItsOwnBucket() {
        augustLogs(
                day(3, AttendanceStatus.PRESENT, 540, 0, 0),
                day(4, AttendanceStatus.PRESENT, 500, 60, 0),
                day(5, AttendanceStatus.LATE, 480, 0, 30),      // late counts as present AND late
                day(6, AttendanceStatus.ABSENT),
                day(7, AttendanceStatus.HALF_DAY, 240, 0, 0),
                day(10, AttendanceStatus.ON_LEAVE),
                day(11, AttendanceStatus.HOLIDAY),
                day(8, AttendanceStatus.WEEKEND),
                day(9, AttendanceStatus.WEEKEND));

        AttendanceSummaryResponse r = service.recalculateSummary(EMPLOYEE, YEAR, AUGUST);

        assertEquals(3.0, r.presentDays());
        assertEquals(1, r.lateDays());
        assertEquals(1.0, r.absentDays());
        assertEquals(0.5, r.halfDays());
        assertEquals(1.0, r.leaveDays());
        assertEquals(1, r.holidayDays());
        assertEquals(2, r.weekendDays());
        assertEquals(540 + 500 + 480 + 240, r.totalWorkingMins());
        assertEquals(60, r.totalOvertimeMins());
        assertEquals(30, r.totalLateMins());
    }

    @Test
    void aHalfDayLeaveWithNoPunchIsHalfLeaveAndHalfAbsent() {
        augustLogs(day(10, AttendanceStatus.ON_LEAVE), day(11, AttendanceStatus.ON_LEAVE));
        when(classifier.halfDayLeaveDates(eq(EMPLOYEE), any(), any()))
                .thenReturn(Set.of(LocalDate.of(YEAR, AUGUST, 10)));

        AttendanceSummaryResponse r = service.recalculateSummary(EMPLOYEE, YEAR, AUGUST);

        assertEquals(1.5, r.leaveDays(), "0.5 for the half-day leave + 1 for the full-day leave");
        assertEquals(0.5, r.absentDays());
    }

    @Test
    void missingMinuteValuesCountAsZero() {
        augustLogs(day(3, AttendanceStatus.PRESENT, null, null, null), day(4, AttendanceStatus.PRESENT, 100, 20, 5));

        AttendanceSummaryResponse r = service.recalculateSummary(EMPLOYEE, YEAR, AUGUST);

        assertEquals(100, r.totalWorkingMins());
        assertEquals(20, r.totalOvertimeMins());
        assertEquals(5, r.totalLateMins());
    }

    @Test
    void aPastMonthIsCountedFromTheFirstToTheLastDay() {
        augustLogs();

        service.recalculateSummary(EMPLOYEE, YEAR, AUGUST);

        verify(logRepo).findByEmployeeIdAndAttendanceDateBetweenAndIsActiveOrderByAttendanceDateAsc(
                EMPLOYEE, LocalDate.of(YEAR, AUGUST, 1), LocalDate.of(YEAR, AUGUST, 31), 1);
    }

    @Test
    void theCurrentMonthIsCountedUpToYesterdayAndTodayStaysLive() {
        LocalDate now = LocalDate.now();
        LocalDate first = now.withDayOfMonth(1);
        LocalDate expectedTo = now.minusDays(1);

        service.recalculateSummary(EMPLOYEE, now.getYear(), now.getMonthValue());

        verify(logRepo).findByEmployeeIdAndAttendanceDateBetweenAndIsActiveOrderByAttendanceDateAsc(
                EMPLOYEE, first, expectedTo, 1);
    }

    @Test
    void recalculatingUpdatesTheStoredRowInsteadOfAddingASecond() {
        AttendanceSummary stored = AttendanceSummary.builder().summaryId(88L).employeeId(EMPLOYEE).employeeCode("EMP-5")
                .summaryYear(YEAR).summaryMonth(AUGUST).presentDays(new BigDecimal("20")).build();
        when(summaryRepo.findByEmployeeIdAndSummaryYearAndSummaryMonth(EMPLOYEE, YEAR, AUGUST))
                .thenReturn(Optional.of(stored));
        augustLogs(day(3, AttendanceStatus.PRESENT, 480, 0, 0));

        service.recalculateSummary(EMPLOYEE, YEAR, AUGUST);

        assertEquals(88L, stored.getSummaryId());
        assertEquals(0, new BigDecimal("1").compareTo(stored.getPresentDays()), "replaced, not added to the old 20");
        verify(summaryRepo, never()).findNextSequenceValue();
    }

    @Test
    void aFirstRecalculationCreatesTheRowWithTheNextSequenceId() {
        augustLogs(day(3, AttendanceStatus.PRESENT, 480, 0, 0));

        AttendanceSummaryResponse r = service.recalculateSummary(EMPLOYEE, YEAR, AUGUST);

        assertEquals(300L, r.summaryId());
    }

    @Test
    void anImpossiblePeriodIsRefused() {
        for (int[] bad : new int[][] {{YEAR, 0}, {YEAR, 13}, {1999, 5}, {2101, 5}}) {
            assertEquals("INVALID_PERIOD",
                    assertThrows(BusinessRuleException.class,
                            () -> service.recalculateSummary(EMPLOYEE, bad[0], bad[1])).getRuleCode());
        }
        verifyNoInteractions(summaryRepo);
    }

    // ── Attendance percentage ────────────────────────────────

    private AttendanceSummaryResponse augustWith(double present, double absent, double half) {
        AttendanceSummary stored = AttendanceSummary.builder().summaryId(88L).employeeId(EMPLOYEE).employeeCode("EMP-5")
                .summaryYear(YEAR).summaryMonth(AUGUST)
                .presentDays(BigDecimal.valueOf(present)).absentDays(BigDecimal.valueOf(absent))
                .halfDays(BigDecimal.valueOf(half)).build();
        when(summaryRepo.findByEmployeeIdAndSummaryYearAndSummaryMonth(EMPLOYEE, YEAR, AUGUST))
                .thenReturn(Optional.of(stored));
        return service.getEmployeeSummary(EMPLOYEE, YEAR, AUGUST);
    }

    @Test
    void thePercentageIsAttendedDaysOverExpectedDays() {
        // 18 full days, 2 half days (stored as 1.0), 2 absent: 22 expected, 19 attended.
        AttendanceSummaryResponse r = augustWith(18, 2, 1.0);

        assertEquals(22, r.totalWorkingDays());
        assertEquals(86.4, r.attendancePercentage());
    }

    @Test
    void aHalfAbsentDayIsNotRoundedIntoAFullExpectedDay() {
        // 10 full days present, 0.5 absent (half-day leave): 10.5 expected, so 10 / 10.5 = 95.2%, not 10 / 11.
        AttendanceSummaryResponse r = augustWith(10, 0.5, 0);

        assertEquals(95.2, r.attendancePercentage());
    }

    @Test
    void fullAttendanceIsOneHundredAndNoAttendanceIsZero() {
        assertEquals(100.0, augustWith(20, 0, 0).attendancePercentage());
        assertEquals(0.0, augustWith(0, 20, 0).attendancePercentage());
    }

    @Test
    void aMonthWithNothingExpectedShowsZeroPercentNotAnError() {
        AttendanceSummaryResponse r = augustWith(0, 0, 0);

        assertEquals(0, r.totalWorkingDays());
        assertEquals(0.0, r.attendancePercentage());
    }

    @Test
    void leaveHolidaysAndWeekendsAreNotExpectedWorkDays() {
        AttendanceSummary stored = AttendanceSummary.builder().summaryId(88L).employeeId(EMPLOYEE).employeeCode("EMP-5")
                .summaryYear(YEAR).summaryMonth(AUGUST).presentDays(new BigDecimal("10"))
                .leaveDays(new BigDecimal("5")).holidayDays(3).weekendDays(8).build();
        when(summaryRepo.findByEmployeeIdAndSummaryYearAndSummaryMonth(EMPLOYEE, YEAR, AUGUST))
                .thenReturn(Optional.of(stored));

        AttendanceSummaryResponse r = service.getEmployeeSummary(EMPLOYEE, YEAR, AUGUST);

        assertEquals(10, r.totalWorkingDays());
        assertEquals(100.0, r.attendancePercentage());
    }

    @Test
    void minutesAreShownAsHoursAndMinutes() {
        AttendanceSummary stored = AttendanceSummary.builder().summaryId(88L).employeeId(EMPLOYEE).employeeCode("EMP-5")
                .summaryYear(YEAR).summaryMonth(AUGUST).totalWorkingMins(10_085L).totalOvertimeMins(0L)
                .totalLateMins(59L).build();
        when(summaryRepo.findByEmployeeIdAndSummaryYearAndSummaryMonth(EMPLOYEE, YEAR, AUGUST))
                .thenReturn(Optional.of(stored));

        AttendanceSummaryResponse r = service.getEmployeeSummary(EMPLOYEE, YEAR, AUGUST);

        assertEquals("168h 5m", r.totalWorkingHours());
        assertEquals("0h 0m", r.totalOvertimeHours());
        assertEquals("0h 59m", r.totalLateHours());
    }

    // ── Reading a summary ────────────────────────────────────

    @Test
    void aMonthWithNoStoredSummaryComesBackEmptyAndNotCurrent() {
        AttendanceSummaryResponse r = service.getEmployeeSummary(EMPLOYEE, YEAR, AUGUST);

        assertEquals(0.0, r.presentDays());
        assertEquals("August 2026", r.monthLabel());
        assertFalse(r.isCurrentMonth());
        verifyNoInteractions(logRepo);   // a past month never looks at today's log
    }

    private AttendanceSummary storedThisMonth() {
        LocalDate now = LocalDate.now();
        AttendanceSummary stored = AttendanceSummary.builder().summaryId(88L).employeeId(EMPLOYEE).employeeCode("EMP-5")
                .summaryYear(now.getYear()).summaryMonth(now.getMonthValue())
                .presentDays(new BigDecimal("10")).absentDays(new BigDecimal("1")).lateDays(2)
                .totalWorkingMins(4_800L).totalOvertimeMins(60L).totalLateMins(40L).build();
        when(summaryRepo.findByEmployeeIdAndSummaryYearAndSummaryMonth(EMPLOYEE, now.getYear(), now.getMonthValue()))
                .thenReturn(Optional.of(stored));
        return stored;
    }

    private void todayIs(AttendanceStatus status, int work, int overtime, int late) {
        AttendanceLog today = AttendanceLog.builder().attendanceDate(LocalDate.now()).status(status)
                .workingMinutes(work).overtimeMinutes(overtime).lateMinutes(late).build();
        when(logRepo.findByEmployeeIdAndAttendanceDateAndIsActive(EMPLOYEE, LocalDate.now(), 1))
                .thenReturn(Optional.of(today));
    }

    @Test
    void theCurrentMonthAddsTodaysLateArrivalOnTopOfTheStoredDays() {
        AttendanceSummary stored = storedThisMonth();
        todayIs(AttendanceStatus.LATE, 200, 30, 25);
        LocalDate now = LocalDate.now();

        AttendanceSummaryResponse r = service.getEmployeeSummary(EMPLOYEE, now.getYear(), now.getMonthValue());

        assertTrue(r.isCurrentMonth());
        assertEquals(11.0, r.presentDays());
        assertEquals(3, r.lateDays());
        assertEquals(4_800 + 200, r.totalWorkingMins());
        assertEquals(60 + 30, r.totalOvertimeMins());
        assertEquals(40 + 25, r.totalLateMins());
        assertEquals(0, new BigDecimal("10").compareTo(stored.getPresentDays()), "the stored row itself is not changed");
        assertEquals(2, stored.getLateDays());
    }

    @Test
    void todaysAbsentHalfDayAndLeaveGoToTheirOwnCounters() {
        LocalDate now = LocalDate.now();

        storedThisMonth();
        todayIs(AttendanceStatus.ABSENT, 0, 0, 0);
        assertEquals(2.0, service.getEmployeeSummary(EMPLOYEE, now.getYear(), now.getMonthValue()).absentDays());

        todayIs(AttendanceStatus.HALF_DAY, 240, 0, 0);
        assertEquals(0.5, service.getEmployeeSummary(EMPLOYEE, now.getYear(), now.getMonthValue()).halfDays());

        todayIs(AttendanceStatus.ON_LEAVE, 0, 0, 0);
        assertEquals(1.0, service.getEmployeeSummary(EMPLOYEE, now.getYear(), now.getMonthValue()).leaveDays());
    }

    @Test
    void todaysHalfDayLeaveIsHalfLeaveAndHalfAbsentLikeTheRecalculation() {
        storedThisMonth();
        todayIs(AttendanceStatus.ON_LEAVE, 0, 0, 0);
        LocalDate now = LocalDate.now();
        when(classifier.halfDayLeaveDates(EMPLOYEE, now, now)).thenReturn(Set.of(now));

        AttendanceSummaryResponse r = service.getEmployeeSummary(EMPLOYEE, now.getYear(), now.getMonthValue());

        assertEquals(0.5, r.leaveDays());
        assertEquals(1.5, r.absentDays(), "1 stored + 0.5 for today");
    }

    @Test
    void withoutATodayLogTheStoredSummaryIsReturnedAsItIs() {
        storedThisMonth();
        LocalDate now = LocalDate.now();

        AttendanceSummaryResponse r = service.getEmployeeSummary(EMPLOYEE, now.getYear(), now.getMonthValue());

        assertTrue(r.isCurrentMonth());
        assertEquals(10.0, r.presentDays());
        assertEquals(4_800, r.totalWorkingMins());
    }
}
