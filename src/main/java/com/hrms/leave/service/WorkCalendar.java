package com.hrms.leave.service;

import com.hrms.employee.entity.WorkShift;
import com.hrms.leave.entity.HolidayCalendar;
import com.hrms.leave.repository.HolidayCalendarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The one definition of "is this a day off": the weekly off and the day-off holidays. Leave,
 * attendance and overtime all ask here, so they cannot disagree.
 *
 * <ul>
 *   <li><b>Weekly off</b>: any day not listed in the employee's shift {@code workingDays}. With no
 *       shift the company weekend applies (Saturday and Sunday).</li>
 *   <li><b>Day-off holiday</b>: an active calendar entry of type PUBLIC or RELIGIOUS. OPTIONAL and
 *       RESTRICTED holidays are days an employee may choose to take, not days off for everyone.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class WorkCalendar {

    private static final Set<String> DAY_OFF_HOLIDAYS = Set.of("PUBLIC", "RELIGIOUS");
    private static final Set<DayOfWeek> DEFAULT_WEEKEND = Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    private final HolidayCalendarRepository holidayRepo;

    /** The company weekend, used when no shift says otherwise. */
    public static boolean isDefaultWeekend(LocalDate date) {
        return DEFAULT_WEEKEND.contains(date.getDayOfWeek());
    }

    /** True when {@code date} is the employee's weekly off; a null shift means the company weekend. */
    public boolean isWeeklyOff(WorkShift shift, LocalDate date) {
        if (shift == null || shift.getWorkingDays() == null || shift.getWorkingDays().isBlank()) {
            return isDefaultWeekend(date);
        }
        String today = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ROOT);
        Set<String> working = Arrays.stream(shift.getWorkingDays().split(","))
                .map(d -> d.trim().toUpperCase(Locale.ROOT))
                .filter(d -> !d.isEmpty())
                .collect(Collectors.toSet());
        return !working.contains(today);
    }

    /** The dates in {@code [from, to]} that are PUBLIC or RELIGIOUS holidays. */
    public Set<LocalDate> dayOffHolidays(LocalDate from, LocalDate to) {
        return holidayRepo.findHolidaysBetween(from, to).stream()
                .filter(h -> DAY_OFF_HOLIDAYS.contains(h.getHolidayType()))
                .map(HolidayCalendar::getHolidayDate)
                .collect(Collectors.toSet());
    }

    public boolean isDayOffHoliday(LocalDate date) {
        return dayOffHolidays(date, date).contains(date);
    }

    /** Working days in {@code [from, to]}: neither the shift's weekly off nor a day-off holiday. */
    public double countWorkingDays(WorkShift shift, LocalDate from, LocalDate to) {
        Set<LocalDate> holidays = dayOffHolidays(from, to);
        double days = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (!isWeeklyOff(shift, d) && !holidays.contains(d)) days++;
        }
        return days;
    }

    /** Weekly off or day-off holiday: a day nobody is expected to work. */
    public boolean isNonWorkingDay(WorkShift shift, LocalDate date) {
        return isWeeklyOff(shift, date) || isDayOffHoliday(date);
    }
}
