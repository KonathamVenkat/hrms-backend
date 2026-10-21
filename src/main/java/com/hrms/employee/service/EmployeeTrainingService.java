package com.hrms.employee.service;

import com.hrms.employee.dto.request.EmployeeTrainingRequest;
import com.hrms.employee.dto.response.EmployeeTrainingResponse;

import java.util.List;

/** Training records of one employee. Writers are HR_ADMIN only (the controller); a record is never deleted. */
public interface EmployeeTrainingService {
    List<EmployeeTrainingResponse> getAll(Long employeeId);
    EmployeeTrainingResponse add(Long employeeId, EmployeeTrainingRequest request);
    EmployeeTrainingResponse update(Long employeeId, Long id, EmployeeTrainingRequest request);
    void deactivate(Long employeeId, Long id);
    void activate(Long employeeId, Long id);
}
