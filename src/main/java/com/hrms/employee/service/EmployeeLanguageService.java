package com.hrms.employee.service;

import com.hrms.employee.dto.request.EmployeeLanguageRequest;
import com.hrms.employee.dto.response.EmployeeLanguageResponse;

import java.util.List;

/** Language records of one employee. Writers are HR_ADMIN only (the controller); a record is never deleted. */
public interface EmployeeLanguageService {
    List<EmployeeLanguageResponse> getAll(Long employeeId);
    EmployeeLanguageResponse add(Long employeeId, EmployeeLanguageRequest request);
    EmployeeLanguageResponse update(Long employeeId, Long id, EmployeeLanguageRequest request);
    void deactivate(Long employeeId, Long id);
    void activate(Long employeeId, Long id);
}
