package com.hrms.payroll.repository;

import com.hrms.payroll.entity.QualificationLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QualificationLevelRepository extends JpaRepository<QualificationLevel, Long> {
    List<QualificationLevel> findAllByOrderBySortOrderAscNameAsc();
    List<QualificationLevel> findByIsActiveTrueOrderBySortOrderAscNameAsc();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
