package com.hrms.attendance.service.impl;

import com.hrms.attendance.enums.AttendanceStatus;
import com.hrms.attendance.repository.OvertimeRequestRepository;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.employee.entity.WorkShift;
import com.hrms.leave.entity.HolidayCalendar;
import com.hrms.leave.entity.LeaveRequest;
import com.hrms.leave.repository.HolidayCalendarRepository;
import com.hrms.leave.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides what kind of day a date is for an employee: a working day, the weekly off, a public
 * holiday, or a day of approved leave. One place, shared by check-in, the nightly record
 * generation and the summary, so they can never disagree.
 *
 * <ul>
 *   <li><b>Weekly off</b>: any day not listed in the employee's shift {@code workingDays}. With no
 *       shift assigned the company weekend applies (Friday and Saturday).</li>
 *   <li><b>Public holiday</b>: an active calendar entry of type PUBLIC or RELIGIOUS. OPTIONAL and
 *       RESTRICTED holidays are days an employee may choose to take, not days off for everyone.</li>
 *   <li><b>Precedence</b>: weekly off, then holiday, then leave. A holiday that falls on the weekly
 *       off is simply the weekend, and leave never claims a day that was off anyway.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AttendanceDayClassifier {

    /** How much of a day an approved leave covers. */
    public enum LeaveCover { NONE, HALF, FULL }

    private static final Set<String> DAY_OFF_HOLIDAYS = Set.of("PUBLIC", "RELIGIOUS");
    private static final Set<DayOfWeek> DEFAULT_WEEKEND = Set.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);

    private final HolidayCalendarRepository holidayRepo;
    private final LeaveRequestRepository    leaveRepo;
    private final OvertimeRequestRepository otRepo;

    // ── The calendar ──────────────────────────────────────────

    /** True when {@code date} is the employee's weekly off. */
    public boolean isWeeklyOff(WorkShift shift, LocalDate date) {
        if (shift == null || shift.getWorkingDays() == null || shift.getWorkingDays().isBlank()) {
            return DEFAULT_WEEKEND.contains(date.getDayOfWeek());
        }
        String today = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ROOT);
        Set<String> working = Arrays.stream(shift.getWorkingDays().split(","))
                .map(d -> d.trim().toUpperCase(Locale.ROOT))
                .filter(d -> !d.isEmpty())
                .collect(Collectors.toSet());
        return !working.contains(today);
    }

    /** True when {@code date} is a public or religious holiday. */
    public boolean isPublicHoliday(LocalDate date) {
        return holidayRepo.findHolidaysBetween(date, date).stream()
                .map(HolidayCalendar::getHolidayType)
                .anyMatch(DAY_OFF_HOLIDAYS::contains);
    }

    /** Weekly off or public holiday: a day nobody is expected to work. */
    public boolean isNonWorkingDay(WorkShift shift, LocalDate date) {
        return isWeeklyOff(shift, date) || isPublicHoliday(date);
    }

    // ── Leave ─────────────────────────────────────────────────

    /** How much of {@code date} the employee has approved leave for. */
    public LeaveCover leaveCover(Long employeeId, LocalDate date) {
        return leaveRepo.findApprovedForEmployeeBetween(employeeId, date, date).stream()
                .map(l -> coverOf(l, date))
                .reduce(LeaveCover.NONE, AttendanceDayClassifier::wider);
    }

    /** Leave cover for every employee on leave on {@code date} (employees without leave are absent from the map). */
    public Map<Long, LeaveCover> leaveCoverByEmployee(LocalDate date) {
        Map<Long, LeaveCover> cover = new HashMap<>();
        for (LeaveRequest l : leaveRepo.findApprovedCovering(date)) {
            cover.merge(l.getEmployeeId(), coverOf(l, date), AttendanceDayClassifier::wider);
        }
        return cover;
    }

    /** Dates in {@code from..to} on which the employee has only a half-day of approved leave. */
    public Set<LocalDate> halfDayLeaveDates(Long employeeId, LocalDate from, LocalDate to) {
        return leaveRepo.findApprovedForEmployeeBetween(employeeId, from, to).stream()
                .filter(AttendanceDayClassifier::isHalfDayLeave)
                .map(LeaveRequest::getStartDate)
                .filter(d -> !d.isBefore(from) && !d.isAfter(to))
                .collect(Collectors.toSet());
    }

    private static LeaveCover coverOf(LeaveRequest leave, LocalDate date) {
        return isHalfDayLeave(leave) && leave.getStartDate().equals(date) ? LeaveCover.HALF : LeaveCover.FULL;
    }

    /** A single-day leave of less than a day. */
    static boolean isHalfDayLeave(LeaveRequest leave) {
        return leave.getStartDate() != null
                && leave.getStartDate().equals(leave.getEndDate())
                && leave.getTotalDays() != null
                && leave.getTotalDays() < 1.0;
    }

    private static LeaveCover wider(LeaveCover a, LeaveCover b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    // ── What a day without a punch is ─────────────────────────

    /**
     * The status of a finished day on which the employee never punched: WEEKEND, HOLIDAY, ON_LEAVE
     * (also for a half-day leave; the summary splits that day) or ABSENT.
     */
    public AttendanceStatus statusWithoutPunch(WorkShift shift, LocalDate date,
                                               boolean publicHoliday, LeaveCover leave) {
        if (isWeeklyOff(shift, date)) return AttendanceStatus.WEEKEND;
        if (publicHoliday)            return AttendanceStatus.HOLIDAY;
        if (leave != LeaveCover.NONE) return AttendanceStatus.ON_LEAVE;
        return AttendanceStatus.ABSENT;
    }

    // ── Check-in rules ────────────────────────────────────────

    /**
     * Throws unless the employee may check in for the attendance day {@code date}: not on a day of
     * full approved leave, and on a weekend or holiday only with approved overtime.
     */
    public void assertMayCheckIn(Long employeeId, WorkShift shift, LocalDate date) {
        boolean weeklyOff = isWeeklyOff(shift, date);
        boolean holiday   = !weeklyOff && isPublicHoliday(date);

        if (!weeklyOff && !holiday && leaveCover(employeeId, date) == LeaveCover.FULL) {
            throw new BusinessRuleException("ON_APPROVED_LEAVE",
                    "You are on approved leave on " + date + ", so you cannot check in. "
                            + "If you are working that day, ask HR to adjust the leave.");
        }
        if (weeklyOff || holiday) {
            Long approvedOt = otRepo.sumApprovedMinutes(employeeId, date);
            if (approvedOt == null || approvedOt <= 0) {
                throw new BusinessRuleException("NON_WORKING_DAY",
                        "Today is a " + (weeklyOff ? "weekend" : "public holiday")
                                + ". Overtime must be approved before you check in: submit an overtime request of type "
                                + "Pre-Approved for this date, and check in once it is approved.");
            }
        }
    }
}
