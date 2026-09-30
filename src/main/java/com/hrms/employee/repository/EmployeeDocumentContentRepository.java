package com.hrms.employee.repository;

import com.hrms.employee.entity.EmployeeDocumentContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmployeeDocumentContentRepository
        extends JpaRepository<EmployeeDocumentContent, Long> {
}
