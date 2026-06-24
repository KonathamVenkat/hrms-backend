package com.hrms.employee.repository;

import com.hrms.employee.entity.Designation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
 
@Repository
public interface DesignationRepository extends JpaRepository<Designation, Long> {
    List<Designation> findByIsActiveOrderByTitleAsc(Integer isActive);
    List<Designation> findByDepartmentIdAndIsActiveOrderByTitleAsc(Long departmentId, Integer isActive);
}