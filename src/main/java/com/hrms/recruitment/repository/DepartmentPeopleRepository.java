package com.hrms.recruitment.repository;

import com.hrms.employee.entity.EmployeeJobDetails;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Read-only view of EMPLOYEE_JOB_DETAILS for the requisition form: who currently works in a department. */
public interface DepartmentPeopleRepository extends Repository<EmployeeJobDetails, Long> {

    @Query("select j.employeeId from EmployeeJobDetails j where j.departmentId = :departmentId and j.isCurrent = 1")
    List<Long> findCurrentEmployeeIds(@Param("departmentId") Long departmentId);
}
