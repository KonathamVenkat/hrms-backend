package com.hrms.employee.repository;

import com.hrms.employee.entity.Section;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SectionRepository extends JpaRepository<Section, Long> {
    List<Section> findAllByOrderByNameAsc();
    List<Section> findByDepartmentIdAndIsActiveOrderByNameAsc(Long departmentId, Boolean isActive);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
    boolean existsByNameIgnoreCaseAndDepartmentId(String name, Long departmentId);
    boolean existsByNameIgnoreCaseAndDepartmentIdAndIdNot(String name, Long departmentId, Long id);
    boolean existsByDepartmentId(Long departmentId);
    boolean existsByDepartmentIdAndIsActive(Long departmentId, Boolean isActive);
}
