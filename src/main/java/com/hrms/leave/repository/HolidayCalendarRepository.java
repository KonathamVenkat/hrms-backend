package com.hrms.leave.repository;

import com.hrms.leave.entity.HolidayCalendar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HolidayCalendarRepository extends JpaRepository<HolidayCalendar, Long> {

    // ── Admin list queries ────────────────────────────────────

    // All holidays for a year — admin view
    List<HolidayCalendar> findByYearOrderByHolidayDateAsc(Integer year);

    // Active holidays for a year — dropdown/leave calc
    List<HolidayCalendar> findByYearAndIsActiveOrderByHolidayDateAsc(
            Integer year, Integer isActive);

    // All active holidays ordered by date
    List<HolidayCalendar> findByIsActiveOrderByHolidayDateAsc(Integer isActive);

    // ── Leave calculation queries ─────────────────────────────

    /**
     * Count active holidays between two dates (inclusive).
     * Used by leave module to count working days.
     */
    @Query("""
        SELECT COUNT(h) FROM HolidayCalendar h
        WHERE h.isActive = 1
          AND h.holidayDate BETWEEN :startDate AND :endDate
        """)
    long countHolidaysBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate")   LocalDate endDate);

    /**
     * Get all active holidays between two dates.
     * Used to build a list of non-working days.
     */
    @Query("""
        SELECT h FROM HolidayCalendar h
        WHERE h.isActive = 1
          AND h.holidayDate BETWEEN :startDate AND :endDate
        ORDER BY h.holidayDate ASC
        """)
    List<HolidayCalendar> findHolidaysBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate")   LocalDate endDate);

    // ── Uniqueness checks ─────────────────────────────────────

    boolean existsByHolidayDateAndHolidayNameIgnoreCase(
            LocalDate holidayDate, String holidayName);

    boolean existsByHolidayDateAndHolidayNameIgnoreCaseAndHolidayIdNot(
            LocalDate holidayDate, String holidayName, Long holidayId);
    
    
    List<HolidayCalendar> findByYearAndIsActive(
            Integer year, Integer isActive);


}
