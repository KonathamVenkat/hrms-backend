package com.hrms.employee.repository;

import com.hrms.employee.entity.WorkShift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkShiftRepository extends JpaRepository<WorkShift, Long> {

    // ── Admin list ────────────────────────────────────────────
    List<WorkShift> findAllByOrderBySortOrderAsc();

    // ── Active only — used for dropdowns in job details ───────
    List<WorkShift> findByIsActiveOrderBySortOrderAsc(Integer isActive);

    // ── Filter by type ────────────────────────────────────────
    List<WorkShift> findByShiftTypeAndIsActiveOrderBySortOrderAsc(
            String shiftType, Integer isActive);

    // ── Uniqueness checks ─────────────────────────────────────
    boolean existsByShiftCodeIgnoreCase(String shiftCode);
    boolean existsByShiftNameIgnoreCase(String shiftName);

    boolean existsByShiftCodeIgnoreCaseAndShiftIdNot(String shiftCode, Long shiftId);
    boolean existsByShiftNameIgnoreCaseAndShiftIdNot(String shiftName, Long shiftId);

    // ── Lookup by code ────────────────────────────────────────
    Optional<WorkShift> findByShiftCodeIgnoreCase(String shiftCode);
}
