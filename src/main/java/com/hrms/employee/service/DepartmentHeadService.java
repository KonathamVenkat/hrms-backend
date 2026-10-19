package com.hrms.employee.service;

import com.hrms.employee.dto.response.DepartmentHeadResponse;

public interface DepartmentHeadService {

    /** The employee's current department and its head. */
    DepartmentHeadResponse get(Long employeeId);

    /** Makes the employee head of their current department (replacing any other head) or clears it. Idempotent. */
    DepartmentHeadResponse set(Long employeeId, boolean head);

    /**
     * Clears the employee as head of every department except keepDepartmentId (null keeps none).
     * Called when the employee moves department or is deactivated, so a leaver never stays head.
     */
    void clearHeadOf(Long employeeId, Long keepDepartmentId);
}
