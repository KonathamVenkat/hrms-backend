package com.hrms.employee.service;

import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.repository.EmployeeRepository;

/** Existence checks shared by the services that hang records off an employee. */
public final class EmployeeChecks {

    private EmployeeChecks() {}

    /** @throws ResourceNotFoundException when there is no employee with this id */
    public static void requireExists(EmployeeRepository employeeRepository, Long employeeId) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException("Employee", "id", employeeId);
        }
    }
}
