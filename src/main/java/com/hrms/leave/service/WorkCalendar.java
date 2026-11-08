package com.hrms.leave.service;

import com.hrms.employee.entity.WorkShift;
import com.hrms.leave.entity.HolidayCalendar;
import com.hrms.leave.repository.HolidayCalendarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.MonthDay;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The one definition of "is this a day off": the weekly off and the day-off holidays. Leave,
 * attendance and overtime all ask here, so they cannot disagree.
 *
 * <ul>
 *   <li><b>Weekly off</b>: any day not listed in the employee's shift {@code workingDays}. With no
 *       shift the company weekend applies (Saturday and Sunday).</li>
 *   <li><b>Day-off holiday</b>: an active calendar entry of type PUBLIC or RELIGIOUS; one marked recurring repeats on
 *       the same month and day in every later year (a 29 February one only in leap years). OPTIONAL and
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

    /** One holiday on one date: the entered date, or a later year's repeat of a recurring one. */
    public record Occurrence(LocalDate date, HolidayCalendar holiday) {
    }

    /** The active holidays (every type) falling in {@code [from, to]}, recurring ones repeated into each year of the range. */
    public List<Occurrence> holidaysBetween(LocalDate from, LocalDate to) {
        Map<String, Occurrence> found = new LinkedHashMap<>();
        for (HolidayCalendar h : holidayRepo.findHolidaysBetween(from, to)) {
            found.put(h.getHolidayId() + "@" + h.getHolidayDate(), new Occurrence(h.getHolidayDate(), h));
        }
        for (HolidayCalendar h : holidayRepo.findByIsRecurringAndIsActive(1, 1)) {
            MonthDay day = MonthDay.from(h.getHolidayDate());
            for (int year = Math.max(from.getYear(), h.getHolidayDate().getYear()); year <= to.getYear(); year++) {
                if (!day.isValidYear(year)) {
                    continue;
                }
                LocalDate date = day.atYear(year);
                if (!date.isBefore(from) && !date.isAfter(to)) {
                    found.putIfAbsent(h.getHolidayId() + "@" + date, new Occurrence(date, h));
                }
            }
        }
        return found.values().stream().sorted(Comparator.comparing(Occurrence::date)).toList();
    }

    /** The dates in {@code [from, to]} that are PUBLIC or RELIGIOUS holidays. */
    public Set<LocalDate> dayOffHolidays(LocalDate from, LocalDate to) {
        return holidaysBetween(from, to).stream()
                .filter(o -> DAY_OFF_HOLIDAYS.contains(o.holiday().getHolidayType()))
                .map(Occurrence::date)
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
