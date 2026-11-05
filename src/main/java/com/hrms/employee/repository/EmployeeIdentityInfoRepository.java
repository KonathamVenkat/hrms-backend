package com.hrms.employee.repository;

import com.hrms.employee.entity.EmployeeIdentityInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmployeeIdentityInfoRepository
        extends JpaRepository<EmployeeIdentityInfo, Long> {

    // ── One-to-one lookup ─────────────────────────────────────
    Optional<EmployeeIdentityInfo> findByEmployeeId(Long employeeId);

    boolean existsByEmployeeId(Long employeeId);

    // ── Uniqueness checks (excluding current record) ──────────
    // The value passed in is already trimmed and upper-cased; the stored one is compared the same way, which is also
    // what the unique indexes of V22 cover, so " ab123" and "AB123" are the same number.
    @Query("SELECT COUNT(i) > 0 FROM EmployeeIdentityInfo i WHERE UPPER(TRIM(i.nationalId)) = :value AND i.employeeId <> :employeeId")
    boolean existsByNationalIdAndEmployeeIdNot(
            @Param("value") String nationalId, @Param("employeeId") Long employeeId);

    @Query("SELECT COUNT(i) > 0 FROM EmployeeIdentityInfo i WHERE UPPER(TRIM(i.passportNumber)) = :value AND i.employeeId <> :employeeId")
    boolean existsByPassportNumberAndEmployeeIdNot(
            @Param("value") String passportNumber, @Param("employeeId") Long employeeId);

    @Query("SELECT COUNT(i) > 0 FROM EmployeeIdentityInfo i WHERE UPPER(TRIM(i.visaNumber)) = :value AND i.employeeId <> :employeeId")
    boolean existsByVisaNumberAndEmployeeIdNot(
            @Param("value") String visaNumber, @Param("employeeId") Long employeeId);

    @Query("SELECT COUNT(i) > 0 FROM EmployeeIdentityInfo i WHERE UPPER(TRIM(i.workPermitNumber)) = :value AND i.employeeId <> :employeeId")
    boolean existsByWorkPermitNumberAndEmployeeIdNot(
            @Param("value") String workPermitNumber, @Param("employeeId") Long employeeId);
}
