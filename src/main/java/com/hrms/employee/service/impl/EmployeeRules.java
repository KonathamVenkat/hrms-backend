package com.hrms.employee.service.impl;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.employee.dto.request.UpdateEmployeeRequest;
import com.hrms.employee.entity.Employee;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

/** Stateless validation rules for employee records, kept apart from the service that applies them. */
public final class EmployeeRules {

    private EmployeeRules() {}

    public static void validateEmploymentDates(
            LocalDate dob,
            LocalDate hireDate,
            LocalDate probationEnd,
            LocalDate confirmationDate) {

        if (dob != null && hireDate != null && hireDate.isBefore(dob)) {
            throw new BusinessRuleException(
                "INVALID_HIRE_DATE", "Hire date cannot be before date of birth.");
        }
        if (hireDate != null && probationEnd != null && probationEnd.isBefore(hireDate)) {
            throw new BusinessRuleException(
                "INVALID_PROBATION_END_DATE", "Probation end date must be after hire date.");
        }
        if (probationEnd != null && confirmationDate != null
                && confirmationDate.isBefore(probationEnd)) {
            throw new BusinessRuleException(
                "INVALID_CONFIRMATION_DATE", "Confirmation date must be after probation end date.");
        }
    }

    /**
     * Only accepts URLs the browser can safely load as an image source (http/https or an
     * app-relative path); anything else is rejected rather than stored and rendered later.
     * Blank clears the photo.
     */
    public static String validatePhotoUrl(String url) {
        if (!StringUtils.hasText(url)) return null;
        String trimmed = url.trim();
        if (trimmed.length() > 500 || !trimmed.matches("^(https?://|/)\\S+$")) {
            throw new BusinessRuleException(
                "INVALID_PHOTO_URL",
                "Profile photo must be an http(s) URL or an app-relative path (max 500 characters).");
        }
        return trimmed;
    }

    public static void validateStatusTransition(Employee current, UpdateEmployeeRequest request) {
        if (request.getEmploymentStatus() == null) return;

        // Leaving employment is recorded through Deactivate, which also revokes the login;
        // setting it here would leave a terminated employee with a working account.
        if (request.getEmploymentStatus() != current.getEmploymentStatus()
                && !request.getEmploymentStatus().isCurrentlyEmployed()) {
            throw new BusinessRuleException(
                "USE_DEACTIVATE_FOR_EXIT",
                "To record " + request.getEmploymentStatus().getDisplayName()
                    + ", use Deactivate instead — it also revokes the employee's login.");
        }

        boolean currentlyExited = !current.getEmploymentStatus().isCurrentlyEmployed();
        boolean newStatusIsActive = request.getEmploymentStatus().isCurrentlyEmployed();

        if (currentlyExited && newStatusIsActive) {
            throw new BusinessRuleException(
                "INVALID_STATUS_TRANSITION",
                String.format(
                    "Cannot transition from '%s' to '%s' via a standard update. "
                    + "Use the reactivation endpoint for rehires.",
                    current.getEmploymentStatus().getDisplayName(),
                    request.getEmploymentStatus().getDisplayName()
                )
            );
        }
    }
}
