package com.hrms.employee.service;

import com.hrms.employee.dto.response.DepartmentSecretaryResponse;

public interface DepartmentSecretaryService {

    /** The employee's current department and its secretary. */
    DepartmentSecretaryResponse get(Long employeeId);

    /** Makes the employee secretary of their current department (replacing any other) or clears it. Idempotent. */
    DepartmentSecretaryResponse set(Long employeeId, boolean secretary);

    /**
     * Clears the employee as secretary of every department except keepDepartmentId (null keeps none).
     * Called when the employee moves department or is deactivated, so a leaver never stays secretary.
     */
    void clearSecretaryOf(Long employeeId, Long keepDepartmentId);
}
