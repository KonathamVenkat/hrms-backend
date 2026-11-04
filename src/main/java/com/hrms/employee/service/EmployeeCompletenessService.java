package com.hrms.employee.service;

import com.hrms.employee.dto.response.EmployeeCompletenessResponse;

public interface EmployeeCompletenessService {

    /** One call that tells which profile tabs of the employee already have their required records. */
    EmployeeCompletenessResponse getCompleteness(Long employeeId);
}
