package com.hrms.employee.repository;

import com.hrms.employee.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {
    List<Department> findByIsActiveOrderByNameAsc(Boolean isActive);
    List<Department> findAllByOrderByNameAsc();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndDeptIdNot(String code, Long deptId);
}
