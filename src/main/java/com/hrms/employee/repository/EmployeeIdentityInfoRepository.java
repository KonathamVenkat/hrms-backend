package com.hrms.employee.repository;

import com.hrms.employee.entity.EmployeeIdentityInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmployeeIdentityInfoRepository
        extends JpaRepository<EmployeeIdentityInfo, Long> {

    // ── One-to-one lookup ─────────────────────────────────────
    Optional<EmployeeIdentityInfo> findByEmployeeId(Long employeeId);

    boolean existsByEmployeeId(Long employeeId);

    // ── Uniqueness checks (excluding current record) ──────────
    boolean existsByNationalIdAndEmployeeIdNot(
            String nationalId, Long employeeId);

    boolean existsByPassportNumberAndEmployeeIdNot(
            String passportNumber, Long employeeId);

    boolean existsByVisaNumberAndEmployeeIdNot(
            String visaNumber, Long employeeId);

    boolean existsByWorkPermitNumberAndEmployeeIdNot(
            String workPermitNumber, Long employeeId);
}
