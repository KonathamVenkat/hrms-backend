package com.hrms.leave.service.impl;

import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.leave.dto.response.*;
import com.hrms.leave.entity.HolidayCalendar;
import com.hrms.leave.entity.LeaveRequest;
import com.hrms.leave.entity.LeaveStatus;
import com.hrms.leave.repository.HolidayCalendarRepository;
import com.hrms.leave.repository.LeaveRequestRepository;
import com.hrms.leave.repository.LeaveTypeRepository;
import com.hrms.leave.service.LeaveCalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveCalendarServiceImpl implements LeaveCalendarService {

    private final LeaveRequestRepository    leaveRequestRepository;
    private final HolidayCalendarRepository holidayRepository;
    private final LeaveTypeRepository       leaveTypeRepository;
    private final EmployeeRepository        employeeRepository;

    @Override
    public CalendarMonthResponse getCurrentMonthCalendar() {
        LocalDate today = LocalDate.now();
        return getMonthCalendar(today.getYear(), today.getMonthValue());
    }

    @Override
    public CalendarMonthResponse getMonthCalendar(int year, int month) {
        log.info("Building calendar for {}/{}", year, month);

        LocalDate firstDay = LocalDate.of(year, month, 1);
        LocalDate lastDay  = firstDay.withDayOfMonth(firstDay.lengthOfMonth());
        LocalDate today    = LocalDate.now();

        // ── Fetch approved + pending leaves overlapping this month ──
        List<LeaveRequest> monthLeaves = leaveRequestRepository
            .findLeavesOverlappingMonth(firstDay, lastDay);

        // ── Fetch active holidays for this year ───────────────────
        List<HolidayCalendar> holidays = holidayRepository
            .findByYearAndIsActiveOrderByHolidayDateAsc(year, 1);

        // ── Index holidays by date ────────────────────────────────
        Map<LocalDate, List<HolidayCalendar>> holidayMap = holidays.stream()
            .filter(h -> h.getHolidayDate() != null)
            .collect(Collectors.groupingBy(HolidayCalendar::getHolidayDate));

        // ── Index leaves by each date they span ───────────────────
        Map<LocalDate, List<LeaveRequest>> leavesByDate = new HashMap<>();
        for (LeaveRequest lr : monthLeaves) {
            LocalDate from = lr.getStartDate().isBefore(firstDay)
                ? firstDay : lr.getStartDate();
            LocalDate to   = lr.getEndDate().isAfter(lastDay)
                ? lastDay  : lr.getEndDate();
            LocalDate cur  = from;
            while (!cur.isAfter(to)) {
                leavesByDate.computeIfAbsent(cur, k -> new ArrayList<>()).add(lr);
                cur = cur.plusDays(1);
            }
        }

        // ── Preload employee name map ─────────────────────────────
        Set<Long> empIds = monthLeaves.stream()
            .map(LeaveRequest::getEmployeeId)
            .collect(Collectors.toSet());
        Map<Long, Employee> empMap = empIds.isEmpty()
            ? Collections.emptyMap()
            : employeeRepository.findAllById(empIds).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        // ── Leave type name map ───────────────────────────────────
        Map<String, String> typeNameMap = new HashMap<>();
        leaveTypeRepository.findAllByOrderBySortOrderAsc()
            .forEach(lt -> typeNameMap.put(lt.getCode(), lt.getNameEn()));

        // ── Build calendar grid (Mon → Sun weeks) ─────────────────
        LocalDate gridStart = firstDay.with(
            TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate gridEnd = lastDay.with(
            TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        List<CalendarDayResponse> days = new ArrayList<>();
        int totalApproved = 0;
        int totalPending  = 0;

        LocalDate cursor = gridStart;
        while (!cursor.isAfter(gridEnd)) {
            final LocalDate d = cursor;
            boolean isCurrentMonth = d.getMonthValue() == month;

            // Oman weekend: Friday + Saturday
            boolean isWeekend = d.getDayOfWeek() == DayOfWeek.FRIDAY
                             || d.getDayOfWeek() == DayOfWeek.SATURDAY;

            List<HolidayCalendar> dayHols   = holidayMap.getOrDefault(d, List.of());
            List<LeaveRequest>    dayLeaves = leavesByDate.getOrDefault(d, List.of());

            // Build leave entries for this day
            List<CalendarLeaveEntry> leaveEntries = dayLeaves.stream()
                .map(lr -> buildLeaveEntry(lr, empMap, typeNameMap))
                .collect(Collectors.toList());

            // Build holiday entries for this day
            List<CalendarHolidayEntry> holEntries = dayHols.stream()
                .map(h -> CalendarHolidayEntry.builder()
                    .holidayId(h.getHolidayId())
                    .holidayName(h.getHolidayName())
                    .holidayNameAr(h.getHolidayNameAr())
                    .holidayType(h.getHolidayType())
                    .build())
                .collect(Collectors.toList());

            long approved = leaveEntries.stream()
                .filter(l -> "APPROVED".equals(l.getStatus())).count();
            long pending  = leaveEntries.stream()
                .filter(l -> "PENDING".equals(l.getStatus())).count();

            if (isCurrentMonth) {
                totalApproved += (int) approved;
                totalPending  += (int) pending;
            }

            days.add(CalendarDayResponse.builder()
                .date(d.toString())
                .dayOfMonth(d.getDayOfMonth())
                .dayOfWeek(d.getDayOfWeek().name())
                .isWeekend(isWeekend)
                .isHoliday(!dayHols.isEmpty())
                .isToday(d.equals(today))
                .isCurrentMonth(isCurrentMonth)
                .holidays(holEntries)
                .leaves(leaveEntries)
                .onLeaveCount((int) approved)
                .pendingCount((int) pending)
                .build());

            cursor = cursor.plusDays(1);
        }

        // ── Today's approved leaves ───────────────────────────────
        List<CalendarLeaveEntry> todaysLeaves = leavesByDate
            .getOrDefault(today, List.of()).stream()
            .filter(lr -> LeaveStatus.APPROVED == lr.getStatus())
            .map(lr -> buildLeaveEntry(lr, empMap, typeNameMap))
            .collect(Collectors.toList());

        // ── Holidays count for this month ────────────────────────
        int thisMonthHolidays = (int) holidays.stream()
            .filter(h -> h.getHolidayDate() != null
                && h.getHolidayDate().getMonthValue() == month)
            .count();

        String monthName = firstDay.getMonth()
            .getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year;

        return CalendarMonthResponse.builder()
            .year(year)
            .month(month)
            .monthName(monthName)
            .days(days)
            .totalApproved(totalApproved)
            .totalPending(totalPending)
            .totalHolidays(thisMonthHolidays)
            .onLeaveToday(todaysLeaves.size())
            .todaysLeaves(todaysLeaves)
            .build();
    }

    // ── Private helpers ───────────────────────────────────────

    private CalendarLeaveEntry buildLeaveEntry(
            LeaveRequest lr,
            Map<Long, Employee> empMap,
            Map<String, String> typeNameMap) {

        Employee emp  = empMap.get(lr.getEmployeeId());
        String   name = emp != null
            ? emp.getFirstName() + " " + emp.getLastName()
            : lr.getEmployeeCode();

        return CalendarLeaveEntry.builder()
            .employeeId(lr.getEmployeeId())
            .employeeCode(lr.getEmployeeCode())
            .employeeName(name)
            .leaveTypeCode(lr.getLeaveTypeCode())
            .leaveTypeName(typeNameMap.getOrDefault(
                lr.getLeaveTypeCode(), lr.getLeaveTypeCode()))
            .status(lr.getStatus() != null ? lr.getStatus().name() : "PENDING")
            .startDate(lr.getStartDate().toString())
            .endDate(lr.getEndDate().toString())
            .build();
    }
}
