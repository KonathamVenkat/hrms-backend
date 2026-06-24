package com.hrms.attendance.repository;

import com.hrms.attendance.entity.AttendanceSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceSummaryRepository extends JpaRepository<AttendanceSummary, Long> {

    // ── Sequence ──────────────────────────────────────────────
    @Query(value = "SELECT HRMS.SEQ_ATTENDANCE_SUMMARY.NEXTVAL FROM DUAL", nativeQuery = true)
    Long findNextSequenceValue();

    // ── Single employee month lookup ──────────────────────────
    Optional<AttendanceSummary> findByEmployeeIdAndSummaryYearAndSummaryMonth(
            Long employeeId, Integer year, Integer month);

    // ── Full year trend (12 months) for one employee ──────────
    List<AttendanceSummary> findByEmployeeIdAndSummaryYearOrderBySummaryMonthAsc(
            Long employeeId, Integer year);

    // ── HR view: all employees for a month — paginated ────────
    Page<AttendanceSummary> findBySummaryYearAndSummaryMonthOrderByEmployeeCodeAsc(
            Integer year, Integer month, Pageable pageable);

    // ── Payroll integration: all employees non-paginated ──────
    List<AttendanceSummary> findBySummaryYearAndSummaryMonth(
            Integer year, Integer month);
}
