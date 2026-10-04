package com.hrms.attendance.service.impl;

import com.hrms.attendance.entity.AttendanceLog;
import com.hrms.attendance.enums.AttendanceStatus;
import com.hrms.attendance.repository.AttendanceLogRepository;
import com.hrms.attendance.service.impl.AttendanceDayClassifier.LeaveCover;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.entity.WorkShift;
import com.hrms.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AttendanceDayRecorderTest {

    // Two days ago relative to the real clock: a finished working day (Sun-Thu shift) is chosen per test.
    EmployeeRepository      employees = mock(EmployeeRepository.class);
    AttendanceLogRepository logs      = mock(AttendanceLogRepository.class);
    AttendanceCalculator    calculator = mock(AttendanceCalculator.class);
    AttendanceDayClassifier classifier = mock(AttendanceDayClassifier.class);
    AttendanceDayRecorder   recorder;

    WorkShift shift = WorkShift.builder().workingDays("SUN,MON,TUE,WED,THU").build();
    LocalDate day   = LocalDate.now().minusDays(2);

    @BeforeEach
    void setUp() {
        recorder = new AttendanceDayRecorder(employees, logs, calculator, classifier);
        when(calculator.resolveShift(any())).thenReturn(shift);
        when(logs.findNextSequenceValue()).thenReturn(900L);
        when(classifier.leaveCoverByEmployee(day)).thenReturn(Map.of());
        when(classifier.isPublicHoliday(day)).thenReturn(false);
        when(classifier.statusWithoutPunch(any(), eq(day), eq(false), eq(LeaveCover.NONE)))
                .thenReturn(AttendanceStatus.ABSENT);
    }

    private static Employee employee(long id) {
        Employee e = new Employee();
        e.setId(id);
        e.setEmployeeCode("EMP-" + id);
        return e;
    }

    private static AttendanceLog row(long employeeId, LocalDate date, AttendanceStatus status,
                                     LocalDateTime checkIn, int regularized) {
        return AttendanceLog.builder().logId(1L).employeeId(employeeId).employeeCode("EMP-" + employeeId)
                .attendanceDate(date).status(status).checkInTime(checkIn).isRegularized(regularized).build();
    }

    @Test
    void writesAnAbsentRowForSomeoneWhoNeverPunched() {
        when(employees.findByIsActiveAndHireDateLessThanEqual(true, day)).thenReturn(List.of(employee(5)));
        when(logs.findByAttendanceDateAndIsActive(day, 1)).thenReturn(List.of());

        var result = recorder.generateFor(day);

        ArgumentCaptor<AttendanceLog> saved = ArgumentCaptor.forClass(AttendanceLog.class);
        verify(logs).save(saved.capture());
        assertEquals(AttendanceStatus.ABSENT, saved.getValue().getStatus());
        assertEquals(5L, saved.getValue().getEmployeeId());
        assertEquals(day, saved.getValue().getAttendanceDate());
        assertNull(saved.getValue().getCheckInTime());
        assertEquals(0, saved.getValue().getIsRegularized());
        assertEquals("SYSTEM", saved.getValue().getCreatedBy());
        assertEquals(1, result.created());
        assertEquals(0, result.updated());
        assertTrue(result.employeeIds().contains(5L));
    }

    @Test
    void usesTheClassifiersStatusForWeekendHolidayAndLeave() {
        when(employees.findByIsActiveAndHireDateLessThanEqual(true, day)).thenReturn(List.of(employee(5), employee(6)));
        when(logs.findByAttendanceDateAndIsActive(day, 1)).thenReturn(List.of());
        when(classifier.leaveCoverByEmployee(day)).thenReturn(Map.of(6L, LeaveCover.HALF));
        when(classifier.statusWithoutPunch(any(), eq(day), eq(false), eq(LeaveCover.HALF)))
                .thenReturn(AttendanceStatus.ON_LEAVE);

        recorder.generateFor(day);

        ArgumentCaptor<AttendanceLog> saved = ArgumentCaptor.forClass(AttendanceLog.class);
        verify(logs, times(2)).save(saved.capture());
        assertEquals(AttendanceStatus.ABSENT, saved.getAllValues().get(0).getStatus());
        assertEquals(AttendanceStatus.ON_LEAVE, saved.getAllValues().get(1).getStatus());
        assertTrue(saved.getAllValues().get(1).getNotes().contains("half-day"));
    }

    @Test
    void neverTouchesARealPunchOrACorrectedDay() {
        when(employees.findByIsActiveAndHireDateLessThanEqual(true, day))
                .thenReturn(List.of(employee(5), employee(6), employee(7)));
        when(logs.findByAttendanceDateAndIsActive(day, 1)).thenReturn(List.of(
                row(5, day, AttendanceStatus.PRESENT, day.atTime(9, 0), 0),   // punched
                row(6, day, AttendanceStatus.PRESENT, null, 1),               // regularized
                row(7, day, AttendanceStatus.ABSENT, null, 0)));              // generated, unchanged

        var result = recorder.generateFor(day);

        verify(logs, never()).save(any());
        assertEquals(0, result.created() + result.updated());
    }

    @Test
    void correctsAGeneratedRowWhenALeaveWasApprovedLate() {
        AttendanceLog generated = row(5, day, AttendanceStatus.ABSENT, null, 0);
        when(employees.findByIsActiveAndHireDateLessThanEqual(true, day)).thenReturn(List.of(employee(5)));
        when(logs.findByAttendanceDateAndIsActive(day, 1)).thenReturn(List.of(generated));
        when(classifier.leaveCoverByEmployee(day)).thenReturn(Map.of(5L, LeaveCover.FULL));
        when(classifier.statusWithoutPunch(any(), eq(day), eq(false), eq(LeaveCover.FULL)))
                .thenReturn(AttendanceStatus.ON_LEAVE);

        var result = recorder.generateFor(day);

        assertEquals(AttendanceStatus.ON_LEAVE, generated.getStatus());
        verify(logs).save(generated);
        assertEquals(0, result.created());
        assertEquals(1, result.updated());
    }

    @Test
    void skipsTodayBecauseTheDayIsNotOver() {
        var result = recorder.generateFor(LocalDate.now());

        verifyNoInteractions(employees);
        verify(logs, never()).save(any());
        assertEquals(0, result.created());
    }
}
