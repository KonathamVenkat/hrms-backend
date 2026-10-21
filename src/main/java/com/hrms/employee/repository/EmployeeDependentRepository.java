package com.hrms.employee.repository;

import com.hrms.employee.entity.EmployeeDependent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeDependentRepository extends JpaRepository<EmployeeDependent, Long> {
    List<EmployeeDependent> findByEmployeeIdOrderByIdAsc(Long employeeId);
}
