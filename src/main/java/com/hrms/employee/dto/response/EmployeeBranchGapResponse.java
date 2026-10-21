package com.hrms.employee.dto.response;

/** A list row for the back-fill screen; list DTOs carry no personal details beyond name and department. */
public record EmployeeBranchGapResponse(
    Long employeeId,
    String employeeCode,
    String fullName,
    String fullNameAr,
    String departmentName,
    Long locationId,
    String locationName) {
}
