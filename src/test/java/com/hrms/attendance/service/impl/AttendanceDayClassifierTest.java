package com.hrms.attendance.service.impl;

import com.hrms.attendance.enums.AttendanceStatus;
import com.hrms.attendance.repository.OvertimeRequestRepository;
import com.hrms.attendance.service.impl.AttendanceDayClassifier.LeaveCover;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.employee.entity.WorkShift;
import com.hrms.leave.entity.HolidayCalendar;
import com.hrms.leave.entity.LeaveRequest;
import com.hrms.leave.repository.HolidayCalendarRepository;
import com.hrms.leave.repository.LeaveRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttendanceDayClassifierTest {

    // October 2026: Fri 2nd, Sat 3rd, Sun 4th, Mon 5th.
    static final LocalDate FRIDAY = LocalDate.of(2026, 10, 2);
    static final LocalDate SATURDAY = LocalDate.of(2026, 10, 3);
    static final LocalDate SUNDAY = LocalDate.of(2026, 10, 4);
    static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

    HolidayCalendarRepository holidays = mock(HolidayCalendarRepository.class);
    LeaveRequestRepository    leaves   = mock(LeaveRequestRepository.class);
    OvertimeRequestRepository ot       = mock(OvertimeRequestRepository.class);
    AttendanceDayClassifier   classifier;

    WorkShift sunThu = WorkShift.builder().workingDays("SUN,MON,TUE,WED,THU").build();

    @BeforeEach
    void setUp() {
        classifier = new AttendanceDayClassifier(holidays, leaves, ot);
    }

    private static LeaveRequest leave(long employeeId, LocalDate from, LocalDate to, double days) {
        LeaveRequest l = new LeaveRequest();
        l.setEmployeeId(employeeId);
        l.setStartDate(from);
        l.setEndDate(to);
        l.setTotalDays(days);
        return l;
    }

    private void holidayOn(LocalDate date, String type) {
        HolidayCalendar h = HolidayCalendar.builder().holidayDate(date).holidayType(type).build();
        when(holidays.findHolidaysBetween(date, date)).thenReturn(List.of(h));
    }

    // ── The calendar ─────────────────────────────────────────

    @Test
    void theShiftsWorkingDaysDecideTheWeeklyOff() {
        assertTrue(classifier.isWeeklyOff(sunThu, FRIDAY));
        assertTrue(classifier.isWeeklyOff(sunThu, SATURDAY));
        assertFalse(classifier.isWeeklyOff(sunThu, SUNDAY));
        assertFalse(classifier.isWeeklyOff(sunThu, MONDAY));

        WorkShift monFri = WorkShift.builder().workingDays(" mon, tue ,wed,thu,fri ").build();
        assertFalse(classifier.isWeeklyOff(monFri, FRIDAY));
        assertTrue(classifier.isWeeklyOff(monFri, SUNDAY));
    }

    @Test
    void withoutAShiftTheCompanyWeekendIsFridayAndSaturday() {
        assertTrue(classifier.isWeeklyOff(null, FRIDAY));
        assertTrue(classifier.isWeeklyOff(null, SATURDAY));
        assertFalse(classifier.isWeeklyOff(null, SUNDAY));
    }

    @Test
    void onlyPublicAndReligiousHolidaysAreDaysOff() {
        holidayOn(MONDAY, "PUBLIC");
        assertTrue(classifier.isPublicHoliday(MONDAY));
        holidayOn(MONDAY, "RELIGIOUS");
        assertTrue(classifier.isPublicHoliday(MONDAY));
        holidayOn(MONDAY, "OPTIONAL");
        assertFalse(classifier.isPublicHoliday(MONDAY));
        holidayOn(MONDAY, "RESTRICTED");
        assertFalse(classifier.isPublicHoliday(MONDAY));
        assertFalse(classifier.isPublicHoliday(SUNDAY));
    }

    // ── Leave ────────────────────────────────────────────────

    @Test
    void leaveCoverIsFullHalfOrNone() {
        when(leaves.findApprovedForEmployeeBetween(5L, SUNDAY, SUNDAY))
                .thenReturn(List.of(leave(5, SUNDAY.minusDays(1), SUNDAY.plusDays(1), 3)));
        when(leaves.findApprovedForEmployeeBetween(6L, SUNDAY, SUNDAY))
                .thenReturn(List.of(leave(6, SUNDAY, SUNDAY, 0.5)));

        assertEquals(LeaveCover.FULL, classifier.leaveCover(5L, SUNDAY));
        assertEquals(LeaveCover.HALF, classifier.leaveCover(6L, SUNDAY));
        assertEquals(LeaveCover.NONE, classifier.leaveCover(7L, SUNDAY));
    }

    @Test
    void aFullDayLeaveWinsOverAHalfDayOnTheSameDate() {
        when(leaves.findApprovedCovering(SUNDAY)).thenReturn(List.of(
                leave(5, SUNDAY, SUNDAY, 0.5), leave(5, SUNDAY.minusDays(2), SUNDAY.plusDays(2), 5)));

        assertEquals(Map.of(5L, LeaveCover.FULL), classifier.leaveCoverByEmployee(SUNDAY));
    }

    @Test
    void halfDayLeaveDatesAreSingleDayLeavesOfLessThanADay() {
        when(leaves.findApprovedForEmployeeBetween(5L, SUNDAY, MONDAY)).thenReturn(List.of(
                leave(5, SUNDAY, SUNDAY, 0.5), leave(5, MONDAY, MONDAY, 1.0)));

        assertEquals(Set.of(SUNDAY), classifier.halfDayLeaveDates(5L, SUNDAY, MONDAY));
    }

    // ── What a day without a punch is ────────────────────────

    @Test
    void weeklyOffBeatsHolidayBeatsLeaveBeatsAbsent() {
        assertEquals(AttendanceStatus.WEEKEND,
                classifier.statusWithoutPunch(sunThu, FRIDAY, true, LeaveCover.FULL));
        assertEquals(AttendanceStatus.HOLIDAY,
                classifier.statusWithoutPunch(sunThu, MONDAY, true, LeaveCover.FULL));
        assertEquals(AttendanceStatus.ON_LEAVE,
                classifier.statusWithoutPunch(sunThu, MONDAY, false, LeaveCover.HALF));
        assertEquals(AttendanceStatus.ABSENT,
                classifier.statusWithoutPunch(sunThu, MONDAY, false, LeaveCover.NONE));
    }

    // ── Check-in rules ───────────────────────────────────────

    @Test
    void anOrdinaryWorkingDayIsAllowed() {
        assertDoesNotThrow(() -> classifier.assertMayCheckIn(5L, sunThu, MONDAY));
    }

    @Test
    void aWeekendNeedsApprovedOvertime() {
        var ex = assertThrows(BusinessRuleException.class, () -> classifier.assertMayCheckIn(5L, sunThu, FRIDAY));
        assertEquals("NON_WORKING_DAY", ex.getRuleCode());
        assertTrue(ex.getMessage().contains("weekend"));
        assertTrue(ex.getMessage().contains("Pre-Approved"), "must tell the employee which overtime type to file");

        when(ot.sumApprovedMinutes(5L, FRIDAY)).thenReturn(120L);
        assertDoesNotThrow(() -> classifier.assertMayCheckIn(5L, sunThu, FRIDAY));
    }

    @Test
    void aPublicHolidayNeedsApprovedOvertime() {
        holidayOn(MONDAY, "PUBLIC");

        var ex = assertThrows(BusinessRuleException.class, () -> classifier.assertMayCheckIn(5L, sunThu, MONDAY));
        assertEquals("NON_WORKING_DAY", ex.getRuleCode());
        assertTrue(ex.getMessage().contains("public holiday"));

        when(ot.sumApprovedMinutes(5L, MONDAY)).thenReturn(60L);
        assertDoesNotThrow(() -> classifier.assertMayCheckIn(5L, sunThu, MONDAY));
    }

    @Test
    void aFullDayOfApprovedLeaveBlocksCheckIn() {
        when(leaves.findApprovedForEmployeeBetween(5L, MONDAY, MONDAY))
                .thenReturn(List.of(leave(5, MONDAY, MONDAY, 1.0)));

        var ex = assertThrows(BusinessRuleException.class, () -> classifier.assertMayCheckIn(5L, sunThu, MONDAY));
        assertEquals("ON_APPROVED_LEAVE", ex.getRuleCode());
    }

    @Test
    void aHalfDayOfLeaveStillAllowsCheckIn() {
        when(leaves.findApprovedForEmployeeBetween(5L, MONDAY, MONDAY))
                .thenReturn(List.of(leave(5, MONDAY, MONDAY, 0.5)));

        assertDoesNotThrow(() -> classifier.assertMayCheckIn(5L, sunThu, MONDAY));
    }

    @Test
    void leaveDoesNotChangeWhatAWeekendNeeds() {
        when(leaves.findApprovedForEmployeeBetween(any(), any(), any()))
                .thenReturn(List.of(leave(5, FRIDAY.minusDays(2), FRIDAY.plusDays(2), 5)));

        var ex = assertThrows(BusinessRuleException.class, () -> classifier.assertMayCheckIn(5L, sunThu, FRIDAY));
        assertEquals("NON_WORKING_DAY", ex.getRuleCode());
    }
}
