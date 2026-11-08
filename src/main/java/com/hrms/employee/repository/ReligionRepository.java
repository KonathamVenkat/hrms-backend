package com.hrms.employee.repository;

import com.hrms.employee.entity.Religion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReligionRepository extends JpaRepository<Religion, Long> {
    List<Religion> findAllByOrderByNameAsc();
    List<Religion> findByIsActiveOrderByNameAsc(Boolean isActive);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
