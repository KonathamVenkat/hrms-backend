package com.hrms.employee.dto.response;

/**
 * Which tabs of an employee profile already hold the records the guided flow requires. Plain facts: whether a
 * viewer is exempt (cannot add records) is the screen's business, not this endpoint's. {@code bank} and
 * {@code documents} are only filled in for an HR_ADMIN, the one role that adds them; for everyone else they are
 * null (left out of the JSON), so the endpoint reveals nothing about records that role may not see.
 */
public record EmployeeCompletenessResponse(
    boolean job,
    boolean addresses,
    boolean identity,
    Boolean bank,
    Boolean documents,
    boolean family,
    boolean qualifications,
    boolean experience) {

    /** The same answer with the two HR-only tabs left out. */
    public EmployeeCompletenessResponse withoutHrOnlyTabs() {
        return new EmployeeCompletenessResponse(job, addresses, identity, null, null, family, qualifications, experience);
    }
}
