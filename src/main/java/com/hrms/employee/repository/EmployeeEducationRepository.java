package com.hrms.employee.repository;

import com.hrms.employee.entity.EmployeeEducation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeEducationRepository extends JpaRepository<EmployeeEducation, Long> {
    List<EmployeeEducation> findByEmployeeIdOrderByIdAsc(Long employeeId);
}
