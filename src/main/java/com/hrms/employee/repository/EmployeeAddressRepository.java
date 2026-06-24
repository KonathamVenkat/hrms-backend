package com.hrms.employee.repository;

import com.hrms.employee.entity.EmployeeAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeAddressRepository
        extends JpaRepository<EmployeeAddress, Long> {

    // ── All addresses for an employee ─────────────────────────
    List<EmployeeAddress> findByEmployeeIdAndIsActiveOrderByAddressTypeAsc(
            Long employeeId, Integer isActive);

    List<EmployeeAddress> findByEmployeeIdOrderByAddressTypeAsc(Long employeeId);

    // ── Specific address type ──────────────────────────────────
    Optional<EmployeeAddress> findByEmployeeIdAndAddressTypeAndIsActive(
            Long employeeId, String addressType, Integer isActive);

    // ── Primary address ───────────────────────────────────────
    Optional<EmployeeAddress> findByEmployeeIdAndIsPrimaryAndIsActive(
            Long employeeId, Integer isPrimary, Integer isActive);

    // ── Check duplicate type ──────────────────────────────────
    boolean existsByEmployeeIdAndAddressTypeAndIsActive(
            Long employeeId, String addressType, Integer isActive);

    boolean existsByEmployeeIdAndAddressTypeAndIsActiveAndEmployeeAddressesIdNot(
            Long employeeId, String addressType, Integer isActive, Long id);

    // ── Unset primary on all addresses before setting new one ──
    @Modifying
    @Query("""
        UPDATE EmployeeAddress a
        SET a.isPrimary = 0,
            a.updatedAt = CURRENT_TIMESTAMP
        WHERE a.employeeId = :employeeId
          AND a.isActive   = 1
        """)
    int unsetAllPrimary(@Param("employeeId") Long employeeId);

    // ── Count active addresses ─────────────────────────────────
    long countByEmployeeIdAndIsActive(Long employeeId, Integer isActive);
}
