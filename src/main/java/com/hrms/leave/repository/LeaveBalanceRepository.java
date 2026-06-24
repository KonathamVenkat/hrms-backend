package com.hrms.leave.repository;

import com.hrms.leave.entity.LeaveBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

    // ── Single record lookup ──────────────────────────────────

    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeCodeAndYear(
            Long employeeId, String leaveTypeCode, Integer year);

    // ── All balances for an employee ──────────────────────────

    List<LeaveBalance> findByEmployeeIdAndYear(
            Long employeeId, Integer year);

    List<LeaveBalance> findByEmployeeId(Long employeeId);

    // ── Existence check ───────────────────────────────────────

    boolean existsByEmployeeIdAndLeaveTypeCodeAndYear(
            Long employeeId, String leaveTypeCode, Integer year);

    // ── All balances for a year — used for bulk init ──────────

    List<LeaveBalance> findByYear(Integer year);

    @Query("""
        SELECT lb FROM LeaveBalance lb
        WHERE lb.year = :year
          AND lb.employeeId IN :employeeIds
        """)
    List<LeaveBalance> findByYearAndEmployeeIdIn(
            @Param("year")        Integer       year,
            @Param("employeeIds") List<Long>    employeeIds);

    // ── Which employees already have balances for year ────────

    @Query("""
        SELECT DISTINCT lb.employeeId FROM LeaveBalance lb
        WHERE lb.year = :year
        """)
    List<Long> findEmployeeIdsWithBalancesForYear(
            @Param("year") Integer year);

    // ── Carry forward query ───────────────────────────────────

    /**
     * Gets annual leave balance from the previous year for carry-forward.
     * Only fetches ANNUAL type since other types typically don't carry forward.
     */
    @Query("""
        SELECT lb FROM LeaveBalance lb
        WHERE lb.employeeId   = :employeeId
          AND lb.leaveTypeCode = :leaveTypeCode
          AND lb.year          = :year
        """)
    Optional<LeaveBalance> findPreviousYearBalance(
            @Param("employeeId")    Long    employeeId,
            @Param("leaveTypeCode") String  leaveTypeCode,
            @Param("year")          Integer year);

    // ── Summary queries ───────────────────────────────────────

    @Query(value = """
        SELECT
            lb.LEAVE_TYPE          AS leaveTypeCode,
            SUM(lb.TOTAL_DAYS)     AS totalDays,
            SUM(lb.USED_DAYS)      AS usedDays,
            SUM(lb.PENDING_DAYS)   AS pendingDays,
            COUNT(lb.BALANCE_ID)   AS employeeCount
        FROM HRMS.LEAVE_BALANCES lb
        WHERE lb.YEAR = :year
        GROUP BY lb.LEAVE_TYPE
        ORDER BY lb.LEAVE_TYPE
        """, nativeQuery = true)
    List<Object[]> getYearSummary(@Param("year") Integer year);

    // ── Reset pending days (called when request cancelled) ────

    @Modifying
    @Query("""
        UPDATE LeaveBalance lb
        SET lb.pendingDays = 0,
            lb.updatedAt = CURRENT_TIMESTAMP
        WHERE lb.employeeId    = :employeeId
          AND lb.leaveTypeCode = :leaveTypeCode
          AND lb.year          = :year
        """)
    int resetPendingDays(
            @Param("employeeId")    Long    employeeId,
            @Param("leaveTypeCode") String  leaveTypeCode,
            @Param("year")          Integer year);
}
