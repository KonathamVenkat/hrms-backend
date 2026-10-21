package com.hrms.employee.service;

import com.hrms.employee.dto.request.EmployeeReferenceRequest;
import com.hrms.employee.dto.response.EmployeeReferenceResponse;

import java.util.List;

/** References of one employee. Writers are HR_ADMIN only (the controller); a record is never deleted. */
public interface EmployeeReferenceService {
    List<EmployeeReferenceResponse> getAll(Long employeeId);
    EmployeeReferenceResponse add(Long employeeId, EmployeeReferenceRequest request);
    EmployeeReferenceResponse update(Long employeeId, Long id, EmployeeReferenceRequest request);
    void deactivate(Long employeeId, Long id);
    void activate(Long employeeId, Long id);
}
