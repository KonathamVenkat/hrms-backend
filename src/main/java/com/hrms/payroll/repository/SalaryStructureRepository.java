package com.hrms.payroll.repository;

import com.hrms.payroll.entity.SalaryStructure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SalaryStructureRepository
        extends JpaRepository<SalaryStructure, Long> {

    @Query(value = "SELECT HRMS.SEQ_SALARY_STRUCTURES.NEXTVAL FROM DUAL",
            nativeQuery = true)
    Long findNextSequenceValue();

    List<SalaryStructure> findByIsActiveOrderByStructureNameAsc(Integer isActive);

    boolean existsByStructureCode(String code);
    boolean existsByStructureName(String name);

    // Count how many employees are using a structure
    @Query(value = """
            SELECT COUNT(*) FROM HRMS.EMPLOYEE_SALARY
            WHERE STRUCTURE_ID = :structureId AND IS_CURRENT = 1
            """, nativeQuery = true)
    int countCurrentEmployeesByStructureId(Long structureId);
}
