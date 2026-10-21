package com.hrms.employee.service;

import com.hrms.employee.dto.request.EmployeeDependentRequest;
import com.hrms.employee.dto.response.EmployeeDependentResponse;

import java.util.List;

/** Dependants of one employee. Writers are HR_ADMIN only (the controller); a record is never deleted. */
public interface EmployeeDependentService {
    List<EmployeeDependentResponse> getAll(Long employeeId);
    EmployeeDependentResponse add(Long employeeId, EmployeeDependentRequest request);
    EmployeeDependentResponse update(Long employeeId, Long id, EmployeeDependentRequest request);
    void deactivate(Long employeeId, Long id);
    void activate(Long employeeId, Long id);
}
