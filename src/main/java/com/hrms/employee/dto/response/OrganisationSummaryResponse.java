package com.hrms.employee.dto.response;

/** What is still missing: active employees without a branch, active branches without a manager or a location. */
public record OrganisationSummaryResponse(
    long employeesWithoutBranch,
    long branchesWithoutManager,
    long branchesWithoutLocation) {
}
