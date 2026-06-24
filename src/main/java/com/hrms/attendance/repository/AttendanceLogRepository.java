package com.hrms.attendance.repository;

import com.hrms.attendance.entity.AttendanceLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {

    // ── Sequence ──────────────────────────────────────────────
    @Query(value = "SELECT HRMS.SEQ_ATTENDANCE_LOGS.NEXTVAL FROM DUAL", nativeQuery = true)
    Long findNextSequenceValue();

    // ── Single record ─────────────────────────────────────────
    Optional<AttendanceLog> findByEmployeeIdAndAttendanceDateAndIsActive(
            Long employeeId, LocalDate date, Integer isActive);

    // ── Date range list (drives monthly detail table) ─────────
    List<AttendanceLog> findByEmployeeIdAndAttendanceDateBetweenAndIsActiveOrderByAttendanceDateAsc(
            Long employeeId, LocalDate from, LocalDate to, Integer isActive);

    // ── Paginated HR dashboard — nullable employeeId filter ───
    @Query("""
            SELECT a FROM AttendanceLog a
            WHERE a.isActive = 1
              AND (:employeeId IS NULL OR a.employeeId = :employeeId)
              AND a.attendanceDate BETWEEN :from AND :to
            ORDER BY a.attendanceDate DESC, a.employeeId ASC
            """)
    Page<AttendanceLog> findByFilters(
            @Param("employeeId") Long employeeId,
            @Param("from")       LocalDate from,
            @Param("to")         LocalDate to,
            Pageable pageable);

    // ── Nightly scheduler: all logs for one specific date ─────
    List<AttendanceLog> findByAttendanceDateAndIsActive(LocalDate date, Integer isActive);

    // ── Already checked-in guard ──────────────────────────────
    boolean existsByEmployeeIdAndAttendanceDateAndCheckInTimeIsNotNull(
            Long employeeId, LocalDate date);

    // ── Count by status for a month — native SQL avoids EXTRACT HQL issues ──
    // Caller passes first and last day of month as LocalDate (already computed
    // in AttendanceServiceImpl via LocalDate.of(year,month,1).withDayOfMonth(...))
    @Query(value = """
            SELECT STATUS, COUNT(*)
            FROM HRMS.ATTENDANCE_LOGS
            WHERE EMPLOYEE_ID  = :employeeId
              AND ATTENDANCE_DATE BETWEEN :from AND :to
              AND IS_ACTIVE = 1
            GROUP BY STATUS
            """, nativeQuery = true)
    List<Object[]> countByStatusForMonth(
            @Param("employeeId") Long employeeId,
            @Param("from")       LocalDate from,
            @Param("to")         LocalDate to);

    // ── Sum minutes for a month — native SQL ─────────────────
    @Query(value = """
            SELECT
                NVL(SUM(WORKING_MINUTES),    0),
                NVL(SUM(OVERTIME_MINUTES),   0),
                NVL(SUM(LATE_MINUTES),       0)
            FROM HRMS.ATTENDANCE_LOGS
            WHERE EMPLOYEE_ID  = :employeeId
              AND ATTENDANCE_DATE BETWEEN :from AND :to
              AND IS_ACTIVE = 1
            """, nativeQuery = true)
    Object[] sumMinutesForMonth(
            @Param("employeeId") Long employeeId,
            @Param("from")       LocalDate from,
            @Param("to")         LocalDate to);
}
