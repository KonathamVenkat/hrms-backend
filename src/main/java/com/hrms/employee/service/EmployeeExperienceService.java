package com.hrms.employee.service;

import com.hrms.employee.dto.request.EmployeeExperienceRequest;
import com.hrms.employee.dto.response.EmployeeExperienceResponse;

import java.util.List;

/** Experience records of one employee. Writers are HR_ADMIN only (the controller); a record is never deleted. */
public interface EmployeeExperienceService {
    List<EmployeeExperienceResponse> getAll(Long employeeId);
    EmployeeExperienceResponse add(Long employeeId, EmployeeExperienceRequest request);
    EmployeeExperienceResponse update(Long employeeId, Long id, EmployeeExperienceRequest request);
    void deactivate(Long employeeId, Long id);
    void activate(Long employeeId, Long id);
}
