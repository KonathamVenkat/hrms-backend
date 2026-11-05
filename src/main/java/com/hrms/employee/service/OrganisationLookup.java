package com.hrms.employee.service;

import java.util.Optional;

/**
 * Read-only organisation facts for other modules (workflow approver resolution): who manages a branch, who heads or
 * is secretary of a department. Primitive ids only, so callers never touch employee entities or repositories.
 * A person who is deactivated, or a branch/department that is inactive, counts as nobody.
 */
public interface OrganisationLookup {

    /** Manager of the branch of the employee's current job; empty if no branch, no manager, or either is inactive. */
    Optional<Long> branchManagerOf(long employeeId);

    /** Head of an active department; empty when none, inactive person or inactive department. */
    Optional<Long> headOf(long departmentId);

    /** Secretary of an active department; empty when none, inactive person or inactive department. */
    Optional<Long> secretaryOf(long departmentId);

    /** Department of the employee's current job; empty when no current job or the department is inactive. */
    Optional<Long> departmentOf(long employeeId);

    /** The role of the employee's login (e.g. "HR_ADMIN", "GENERAL_MANAGER"); empty when the employee has no login. */
    Optional<String> accountRoleOf(long employeeId);

    boolean departmentExists(long departmentId);

    boolean isDepartmentActive(long departmentId);
}
