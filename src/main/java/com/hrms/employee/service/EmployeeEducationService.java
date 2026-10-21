package com.hrms.employee.service;

import com.hrms.employee.dto.request.EmployeeEducationRequest;
import com.hrms.employee.dto.response.EmployeeEducationResponse;

import java.util.List;

/** Education records of one employee. Writers are HR_ADMIN only (the controller); a record is never deleted. */
public interface EmployeeEducationService {
    List<EmployeeEducationResponse> getAll(Long employeeId);
    EmployeeEducationResponse add(Long employeeId, EmployeeEducationRequest request);
    EmployeeEducationResponse update(Long employeeId, Long id, EmployeeEducationRequest request);
    void deactivate(Long employeeId, Long id);
    void activate(Long employeeId, Long id);
}
