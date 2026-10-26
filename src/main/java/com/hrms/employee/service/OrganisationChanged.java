package com.hrms.employee.service;

/**
 * Published (inside the changing transaction) after a department head or secretary, or a branch manager or an
 * employee's branch, changed. Listeners in other modules re-read what they derived from the organisation.
 */
public record OrganisationChanged() {
}
