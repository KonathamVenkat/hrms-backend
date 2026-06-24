package com.hrms.attendance.repository;

import com.hrms.attendance.entity.AttendanceRegularization;
import com.hrms.attendance.enums.RegularizationStatus;
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
public interface AttendanceRegularizationRepository
        extends JpaRepository<AttendanceRegularization, Long> {

    // ── Sequence ──────────────────────────────────────────────
    @Query(value = "SELECT HRMS.SEQ_ATTENDANCE_REG.NEXTVAL FROM DUAL",
            nativeQuery = true)
    Long findNextSequenceValue();

    // ── Employee's own requests — paginated ───────────────────
    Page<AttendanceRegularization> findByEmployeeIdAndIsActiveOrderByCreatedAtDesc(
            Long employeeId, Integer isActive, Pageable pageable);

    // ── HR/Manager: all pending requests — paginated ──────────
    Page<AttendanceRegularization> findByStatusAndIsActiveOrderByCreatedAtAsc(
            RegularizationStatus status, Integer isActive, Pageable pageable);

    // ── HR/Manager: filter by status + optional date range ────
    @Query("""
            SELECT r FROM AttendanceRegularization r
            WHERE r.isActive = 1
              AND (:status IS NULL OR r.status = :status)
              AND (:employeeId IS NULL OR r.employeeId = :employeeId)
              AND (:from IS NULL OR r.attendanceDate >= :from)
              AND (:to   IS NULL OR r.attendanceDate <= :to)
            ORDER BY r.createdAt DESC
            """)
    Page<AttendanceRegularization> findByFilters(
            @Param("status")     RegularizationStatus status,
            @Param("employeeId") Long employeeId,
            @Param("from")       LocalDate from,
            @Param("to")         LocalDate to,
            Pageable pageable);

    // ── Check duplicate: one pending per employee per date ────
    boolean existsByEmployeeIdAndAttendanceDateAndStatusAndIsActive(
            Long employeeId, LocalDate date,
            RegularizationStatus status, Integer isActive);

    // ── Find by employee + date (for duplicate guard) ─────────
    Optional<AttendanceRegularization> findByEmployeeIdAndAttendanceDateAndIsActive(
            Long employeeId, LocalDate date, Integer isActive);

    // ── Count pending for badge display ──────────────────────
    long countByStatusAndIsActive(RegularizationStatus status, Integer isActive);
}
