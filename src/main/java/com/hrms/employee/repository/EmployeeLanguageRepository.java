package com.hrms.employee.repository;

import com.hrms.employee.entity.EmployeeLanguage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeLanguageRepository extends JpaRepository<EmployeeLanguage, Long> {
    List<EmployeeLanguage> findByEmployeeIdOrderByIdAsc(Long employeeId);
    boolean existsByEmployeeIdAndLanguageId(Long employeeId, Long languageId);
    boolean existsByEmployeeIdAndLanguageIdAndIdNot(Long employeeId, Long languageId, Long id);
}
