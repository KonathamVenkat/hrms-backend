package com.hrms.employee.repository;

import com.hrms.employee.entity.EmployeeTraining;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeTrainingRepository extends JpaRepository<EmployeeTraining, Long> {
    List<EmployeeTraining> findByEmployeeIdOrderByIdAsc(Long employeeId);
}
