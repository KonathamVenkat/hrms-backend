package com.hrms.employee.repository;

import com.hrms.employee.entity.LanguageLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LanguageLevelRepository extends JpaRepository<LanguageLevel, Long> {
    List<LanguageLevel> findAllByOrderByNameAsc();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
