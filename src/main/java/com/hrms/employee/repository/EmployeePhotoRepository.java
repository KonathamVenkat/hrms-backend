package com.hrms.employee.repository;

import com.hrms.employee.entity.EmployeePhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmployeePhotoRepository extends JpaRepository<EmployeePhoto, Long> {
}
