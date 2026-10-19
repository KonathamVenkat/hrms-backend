package com.hrms.employee.dto.response;

/** An employee's current department and who heads it; head is true when it is this employee. */
public record DepartmentHeadResponse(
        Long departmentId,
        String departmentName,
        String departmentCode,
        boolean head,
        Long currentHeadId,
        String currentHeadName) {
}
