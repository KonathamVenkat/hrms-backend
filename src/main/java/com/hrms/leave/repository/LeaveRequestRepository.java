package com.hrms.leave.repository;

import com.hrms.leave.entity.LeaveRequest;
import com.hrms.leave.entity.LeaveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeaveRequestRepository
        extends JpaRepository<LeaveRequest, Long> {

    // ── Employee's own leaves ─────────────────────────────────

    Page<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(
            Long employeeId, Pageable pageable);

    Page<LeaveRequest> findByEmployeeIdAndStatusOrderByCreatedAtDesc(
            Long employeeId, LeaveStatus status, Pageable pageable);

    Page<LeaveRequest> findByEmployeeIdAndLeaveTypeCodeOrderByCreatedAtDesc(
            Long employeeId, String leaveTypeCode, Pageable pageable);

    // ── Year filter via native query (Oracle EXTRACT) ─────────

    @Query(value = """
        SELECT * FROM HRMS.LEAVE_REQUESTS
        WHERE EMPLOYEE_ID = :employeeId
          AND EXTRACT(YEAR FROM START_DATE) = :year
        ORDER BY CREATED_AT DESC
        """,
        nativeQuery = true,
        countQuery = """
        SELECT COUNT(*) FROM HRMS.LEAVE_REQUESTS
        WHERE EMPLOYEE_ID = :employeeId
          AND EXTRACT(YEAR FROM START_DATE) = :year
        """)
    Page<LeaveRequest> findByEmployeeIdAndYear(
            @Param("employeeId") Long    employeeId,
            @Param("year")       Integer year,
            Pageable             pageable);

    // ── HR/Manager — all requests ─────────────────────────────

    Page<LeaveRequest> findByStatusOrderByCreatedAtDesc(
            LeaveStatus status, Pageable pageable);

    Page<LeaveRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // ── Overlap check — prevent double booking ────────────────

    @Query("""
        SELECT lr FROM LeaveRequest lr
        WHERE lr.employeeId  = :employeeId
          AND lr.status      NOT IN (
              com.hrms.leave.entity.LeaveStatus.REJECTED,
              com.hrms.leave.entity.LeaveStatus.CANCELLED
          )
          AND lr.startDate   <= :endDate
          AND lr.endDate     >= :startDate
        """)
    List<LeaveRequest> findOverlapping(
            @Param("employeeId") Long      employeeId,
            @Param("startDate")  LocalDate startDate,
            @Param("endDate")    LocalDate endDate);

    // ── Calendar — all approved/pending leaves for a month ────

    @Query("""
        SELECT lr FROM LeaveRequest lr
        WHERE lr.status IN (
              com.hrms.leave.entity.LeaveStatus.APPROVED,
              com.hrms.leave.entity.LeaveStatus.PENDING
          )
          AND lr.startDate <= :monthEnd
          AND lr.endDate   >= :monthStart
        ORDER BY lr.startDate ASC
        """)
    List<LeaveRequest> findLeavesOverlappingMonth(
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd")   LocalDate monthEnd);


    
    // ── Attendance: approved leave covering a day / a period ──

    /** Every approved leave that covers {@code date}, for all employees. */
    @Query("""
        SELECT lr FROM LeaveRequest lr
        WHERE lr.status    = com.hrms.leave.entity.LeaveStatus.APPROVED
          AND lr.isActive  = true
          AND lr.startDate <= :date
          AND lr.endDate   >= :date
        """)
    List<LeaveRequest> findApprovedCovering(@Param("date") LocalDate date);

    /** One employee's approved leaves that touch {@code from..to}. */
    @Query("""
        SELECT lr FROM LeaveRequest lr
        WHERE lr.employeeId = :employeeId
          AND lr.status     = com.hrms.leave.entity.LeaveStatus.APPROVED
          AND lr.isActive   = true
          AND lr.startDate  <= :to
          AND lr.endDate    >= :from
        """)
    List<LeaveRequest> findApprovedForEmployeeBetween(
            @Param("employeeId") Long      employeeId,
            @Param("from")       LocalDate from,
            @Param("to")         LocalDate to);

    // ── Count pending ─────────────────────────────────────────

    long countByEmployeeIdAndStatus(Long employeeId, LeaveStatus status);

    long countByStatus(LeaveStatus status);

    // ── Dashboard — distinct employees on approved leave on a date ──

    @Query("""
        SELECT COUNT(DISTINCT lr.employeeId) FROM LeaveRequest lr
        WHERE lr.status    = com.hrms.leave.entity.LeaveStatus.APPROVED
          AND lr.isActive  = true
          AND lr.startDate <= :date
          AND lr.endDate   >= :date
        """)
    long countEmployeesOnApprovedLeave(@Param("date") LocalDate date);
}