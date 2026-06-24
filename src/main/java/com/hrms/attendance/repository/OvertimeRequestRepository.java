package com.hrms.attendance.repository;

import com.hrms.attendance.entity.OvertimeRequest;
import com.hrms.attendance.enums.RegularizationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface OvertimeRequestRepository
        extends JpaRepository<OvertimeRequest, String> {

    // ── Sequence — used to generate OT_ID in Java ─────────
    // Fetches the raw sequence number; service formats it as OT-YYYY-NNNNNN
    @Query(value = "SELECT HRMS.SEQ_OVERTIME_REQ.NEXTVAL FROM DUAL",
            nativeQuery = true)
    Long findNextSequenceValue();

    // ── Employee's own requests — paginated ───────────────
    Page<OvertimeRequest> findByEmployeeIdAndIsActiveOrderByCreatedAtDesc(
            Long employeeId, Integer isActive, Pageable pageable);

    // ── HR/Manager: filtered list ─────────────────────────
    @Query("""
            SELECT o FROM OvertimeRequest o
            WHERE o.isActive = 1
              AND (:status     IS NULL OR o.status     = :status)
              AND (:employeeId IS NULL OR o.employeeId = :employeeId)
              AND (:from       IS NULL OR o.otDate    >= :from)
              AND (:to         IS NULL OR o.otDate    <= :to)
            ORDER BY o.createdAt DESC
            """)
    Page<OvertimeRequest> findByFilters(
            @Param("status")     RegularizationStatus status,
            @Param("employeeId") Long employeeId,
            @Param("from")       LocalDate from,
            @Param("to")         LocalDate to,
            Pageable pageable);

    // ── Payroll integration: approved OT for a period ─────
    @Query(value = """
            SELECT * FROM HRMS.OVERTIME_REQUESTS
            WHERE STATUS    = 'APPROVED'
              AND IS_ACTIVE = 1
              AND OT_DATE   BETWEEN :from AND :to
            """, nativeQuery = true)
    List<OvertimeRequest> findApprovedForPeriod(
            @Param("from") LocalDate from,
            @Param("to")   LocalDate to);

    // ── Duplicate check ───────────────────────────────────
    boolean existsByEmployeeIdAndOtDateAndStatusAndIsActive(
            Long employeeId, LocalDate date,
            RegularizationStatus status, Integer isActive);

    // ── Pending count for badge ───────────────────────────
    long countByStatusAndIsActive(
            RegularizationStatus status, Integer isActive);
}