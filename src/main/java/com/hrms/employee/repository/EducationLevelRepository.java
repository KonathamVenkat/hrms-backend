package com.hrms.employee.repository;

import com.hrms.employee.entity.EducationLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EducationLevelRepository extends JpaRepository<EducationLevel, Long> {
    List<EducationLevel> findAllByOrderByNameAsc();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
    List<EducationLevel> findByIsActiveOrderByNameAsc(Boolean isActive);
}
