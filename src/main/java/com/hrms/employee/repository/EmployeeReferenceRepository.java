package com.hrms.employee.repository;

import com.hrms.employee.entity.EmployeeReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeReferenceRepository extends JpaRepository<EmployeeReference, Long> {
    List<EmployeeReference> findByEmployeeIdOrderByIdAsc(Long employeeId);
}
