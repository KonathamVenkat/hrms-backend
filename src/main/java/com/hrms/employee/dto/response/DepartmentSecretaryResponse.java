package com.hrms.employee.dto.response;

/** An employee's current department and its secretary; secretary is true when it is this employee. */
public record DepartmentSecretaryResponse(
        Long departmentId,
        String departmentName,
        String departmentCode,
        boolean secretary,
        Long currentSecretaryId,
        String currentSecretaryName) {
}
