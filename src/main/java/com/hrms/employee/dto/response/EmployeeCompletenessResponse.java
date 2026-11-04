package com.hrms.employee.dto.response;

/**
 * Which tabs of an employee profile already hold the records the guided flow requires. Plain facts: whether a
 * viewer is exempt (cannot add records) is the screen's business, not this endpoint's.
 */
public record EmployeeCompletenessResponse(
    boolean job,
    boolean addresses,
    boolean identity,
    boolean bank,
    boolean documents,
    boolean family,
    boolean qualifications,
    boolean experience) {
}
