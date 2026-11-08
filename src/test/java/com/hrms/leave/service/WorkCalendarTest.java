package com.hrms.leave.service;

import com.hrms.leave.entity.HolidayCalendar;
import com.hrms.leave.repository.HolidayCalendarRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A holiday marked recurring falls on the same month and day every year from the year it was entered. */
class WorkCalendarTest {

    HolidayCalendarRepository holidays = mock(HolidayCalendarRepository.class);
    WorkCalendar calendar = new WorkCalendar(holidays);

    private HolidayCalendar holiday(long id, String name, LocalDate date, String type, boolean recurring) {
        return HolidayCalendar.builder().holidayId(id).holidayName(name).holidayDate(date).holidayType(type)
            .isRecurring(recurring ? 1 : 0).year(date.getYear()).isActive(1).build();
    }

    @BeforeEach
    void noOneOffHolidays() {
        when(holidays.findHolidaysBetween(any(), any())).thenReturn(List.of());
        when(holidays.findByIsRecurringAndIsActive(1, 1)).thenReturn(List.of());
    }

    @Test
    void aRecurringHolidayIsFoundInLaterYears() {
        when(holidays.findByIsRecurringAndIsActive(1, 1))
            .thenReturn(List.of(holiday(1, "Independence Day", LocalDate.of(2026, 7, 9), "PUBLIC", true)));

        assertEquals(Set.of(LocalDate.of(2027, 7, 9)), calendar.dayOffHolidays(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 31)));
        assertEquals(Set.of(LocalDate.of(2029, 7, 9)), calendar.dayOffHolidays(LocalDate.of(2029, 7, 9), LocalDate.of(2029, 7, 9)));
    }

    @Test
    void aRecurringHolidayIsNotAppliedToYearsBeforeItWasEntered() {
        when(holidays.findByIsRecurringAndIsActive(1, 1))
            .thenReturn(List.of(holiday(1, "Independence Day", LocalDate.of(2026, 7, 9), "PUBLIC", true)));

        assertTrue(calendar.dayOffHolidays(LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 31)).isEmpty());
    }

    @Test
    void aOneOffHolidayIsNotRepeated() {
        when(holidays.findHolidaysBetween(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
            .thenReturn(List.of(holiday(2, "Eid", LocalDate.of(2026, 3, 20), "RELIGIOUS", false)));

        assertEquals(Set.of(LocalDate.of(2026, 3, 20)), calendar.dayOffHolidays(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));
        assertTrue(calendar.dayOffHolidays(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 31)).isEmpty());
    }

    @Test
    void theYearItWasEnteredIsCountedOnce() {
        HolidayCalendar newYear = holiday(3, "New Year", LocalDate.of(2026, 1, 1), "PUBLIC", true);
        when(holidays.findHolidaysBetween(any(), any())).thenReturn(List.of(newYear));
        when(holidays.findByIsRecurringAndIsActive(1, 1)).thenReturn(List.of(newYear));

        assertEquals(1, calendar.holidaysBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)).size());
    }

    @Test
    void aRangeAcrossNewYearFindsBothSides() {
        when(holidays.findByIsRecurringAndIsActive(1, 1)).thenReturn(List.of(
            holiday(4, "Christmas", LocalDate.of(2025, 12, 25), "PUBLIC", true),
            holiday(3, "New Year", LocalDate.of(2026, 1, 1), "PUBLIC", true)));

        assertEquals(Set.of(LocalDate.of(2026, 12, 25), LocalDate.of(2027, 1, 1)),
            calendar.dayOffHolidays(LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 5)));
    }

    @Test
    void aLeapDayHolidayAppearsOnlyInLeapYears() {
        when(holidays.findByIsRecurringAndIsActive(1, 1))
            .thenReturn(List.of(holiday(5, "Leap day", LocalDate.of(2024, 2, 29), "PUBLIC", true)));

        assertTrue(calendar.dayOffHolidays(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 3, 1)).isEmpty());
        assertEquals(Set.of(LocalDate.of(2028, 2, 29)), calendar.dayOffHolidays(LocalDate.of(2028, 2, 1), LocalDate.of(2028, 3, 1)));
    }

    @Test
    void optionalHolidaysDoNotTakeAWorkingDayAway() {
        when(holidays.findByIsRecurringAndIsActive(1, 1))
            .thenReturn(List.of(holiday(6, "Optional day", LocalDate.of(2026, 5, 1), "OPTIONAL", true)));

        assertTrue(calendar.dayOffHolidays(LocalDate.of(2027, 5, 1), LocalDate.of(2027, 5, 1)).isEmpty());
    }

    @Test
    void workingDaysSkipARecurringHolidayInALaterYear() {
        when(holidays.findByIsRecurringAndIsActive(1, 1))
            .thenReturn(List.of(holiday(1, "Independence Day", LocalDate.of(2026, 7, 9), "PUBLIC", true)));

        // Mon 5 Jul to Fri 9 Jul 2027 is five weekdays; Friday 9 July is the holiday.
        assertEquals(4.0, calendar.countWorkingDays(null, LocalDate.of(2027, 7, 5), LocalDate.of(2027, 7, 9)));
    }
}
