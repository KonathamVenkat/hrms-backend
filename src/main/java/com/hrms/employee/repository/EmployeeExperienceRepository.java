package com.hrms.employee.repository;

import com.hrms.employee.entity.EmployeeExperience;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeExperienceRepository extends JpaRepository<EmployeeExperience, Long> {
    List<EmployeeExperience> findByEmployeeIdOrderByIdAsc(Long employeeId);
}
