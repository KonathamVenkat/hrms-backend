package com.hrms.leave.service.impl;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.leave.dto.response.CalendarLeaveEntry;
import com.hrms.leave.dto.response.CalendarMonthResponse;
import com.hrms.leave.entity.LeaveRequest;
import com.hrms.leave.entity.LeaveStatus;
import com.hrms.leave.entity.LeaveType;
import com.hrms.leave.repository.HolidayCalendarRepository;
import com.hrms.leave.repository.LeaveRequestRepository;
import com.hrms.leave.repository.LeaveTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The shared calendar must not leak colleagues' leave types or pending requests to plain employees. */
class LeaveCalendarServiceImplTest {

    LeaveRequestRepository leaves   = mock(LeaveRequestRepository.class);
    HolidayCalendarRepository holidays = mock(HolidayCalendarRepository.class);
    LeaveTypeRepository    types    = mock(LeaveTypeRepository.class);
    EmployeeRepository     employees = mock(EmployeeRepository.class);
    EmployeeAccessGuard    guard    = mock(EmployeeAccessGuard.class);
    LeaveCalendarServiceImpl service;

    static LeaveRequest leave(long id, long emp, String type, LeaveStatus status) {
        return LeaveRequest.builder().leaveReqId(id).employeeId(emp).employeeCode("EMP-" + emp)
                .leaveTypeCode(type).status(status)
                .startDate(LocalDate.of(2026, 10, 14)).endDate(LocalDate.of(2026, 10, 14)).build();
    }

    @BeforeEach
    void setUp() {
        service = new LeaveCalendarServiceImpl(leaves, holidays, types, employees, guard);
        when(leaves.findLeavesOverlappingMonth(any(), any())).thenReturn(List.of(
                leave(1, 7, "SICK", LeaveStatus.APPROVED),
                leave(2, 7, "ANNUAL", LeaveStatus.PENDING),
                leave(3, 5, "SICK", LeaveStatus.PENDING)));
        when(types.findAllByOrderBySortOrderAsc()).thenReturn(List.of(
                LeaveType.builder().code("SICK").nameEn("Sick").build(),
                LeaveType.builder().code("ANNUAL").nameEn("Annual").build()));
        when(holidays.findByYearAndIsActiveOrderByHolidayDateAsc(anyInt(), anyInt())).thenReturn(List.of());
    }

    private List<CalendarLeaveEntry> entriesOn14th(CalendarMonthResponse m) {
        return m.getDays().stream().filter(d -> d.getDate().equals("2026-10-14"))
                .flatMap(d -> d.getLeaves().stream()).toList();
    }

    @Test
    void employeeSeesColleaguesApprovedLeaveWithoutTypeAndNoPendingOnes() {
        when(guard.isHrStaff()).thenReturn(false);
        when(guard.currentEmployeeId()).thenReturn(5L);

        List<CalendarLeaveEntry> entries = entriesOn14th(service.getMonthCalendar(2026, 10));

        assertEquals(2, entries.size());   // colleague's approved + own pending; colleague's pending is gone
        CalendarLeaveEntry colleague = entries.stream().filter(e -> e.getEmployeeId() == 7).findFirst().orElseThrow();
        assertEquals("On leave", colleague.getLeaveTypeName());
        assertEquals("LEAVE", colleague.getLeaveTypeCode());
        CalendarLeaveEntry own = entries.stream().filter(e -> e.getEmployeeId() == 5).findFirst().orElseThrow();
        assertEquals("Sick", own.getLeaveTypeName());
    }

    @Test
    void hrStaffSeeEverything() {
        when(guard.isHrStaff()).thenReturn(true);

        List<CalendarLeaveEntry> entries = entriesOn14th(service.getMonthCalendar(2026, 10));

        assertEquals(3, entries.size());
        assertTrue(entries.stream().noneMatch(e -> "On leave".equals(e.getLeaveTypeName())));
    }
}
