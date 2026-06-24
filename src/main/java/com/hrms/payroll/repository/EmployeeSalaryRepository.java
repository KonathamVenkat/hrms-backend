package com.hrms.payroll.repository;

import com.hrms.payroll.entity.EmployeeSalary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeSalaryRepository
        extends JpaRepository<EmployeeSalary, Long> {

    @Query(value = "SELECT HRMS.SEQ_EMPLOYEE_SALARY.NEXTVAL FROM DUAL",
            nativeQuery = true)
    Long findNextSequenceValue();

    // Current active salary for an employee
    Optional<EmployeeSalary> findByEmployeeIdAndIsCurrent(
            Long employeeId, Integer isCurrent);

    // Full salary history for an employee
    List<EmployeeSalary> findByEmployeeIdOrderByEffectiveFromDesc(Long employeeId);

    // Paginated list for HR dashboard
    @Query("""
            SELECT e FROM EmployeeSalary e
            WHERE e.isCurrent = 1
              AND (:structureId IS NULL OR e.structureId = :structureId)
            ORDER BY e.employeeId
            """)
    Page<EmployeeSalary> findCurrentByFilters(
            @Param("structureId") Long structureId,
            Pageable pageable);

    // Used by payroll run to fetch all active employees' salaries
    List<EmployeeSalary> findByIsCurrentAndStructureId(
            Integer isCurrent, Long structureId);

    // Mark old record as not current
    @Modifying
    @Query("""
            UPDATE EmployeeSalary e
            SET e.isCurrent = 0, e.effectiveTo = CURRENT_DATE
            WHERE e.employeeId = :employeeId AND e.isCurrent = 1
            """)
    void deactivateCurrent(@Param("employeeId") Long employeeId);
}
