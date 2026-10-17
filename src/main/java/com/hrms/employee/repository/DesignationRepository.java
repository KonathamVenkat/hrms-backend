package com.hrms.employee.repository;

import com.hrms.employee.entity.Designation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
 
@Repository
public interface DesignationRepository extends JpaRepository<Designation, Long> {
    List<Designation> findByIsActiveOrderByTitleAsc(Boolean isActive);
    List<Designation> findByDepartmentIdAndIsActiveOrderByTitleAsc(Long departmentId, Boolean isActive);
    boolean existsByDepartmentId(Long departmentId);
    boolean existsByDepartmentIdAndIsActive(Long departmentId, Boolean isActive);
    List<Designation> findAllByOrderByTitleAsc();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndDesigIdNot(String code, Long desigId);
}
