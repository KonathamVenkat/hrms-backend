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
     * The only photo address an employee record may hold: the app's own endpoint for that employee's photo,
     * {@code /api/v1/employees/{id}/photo} with an optional {@code ?v=<number>} cache marker. That is exactly what
     * the photo upload writes, so a stored value never points at another site (whose host would learn the IP address
     * and browser of everyone who opens the profile) or at another employee's photo.
     */
    private static final String PHOTO_PATH = "/api/v1/employees/%d/photo";

    /**
     * Accepts the employee's own photo path and nothing else; blank clears the photo. To change the picture, upload it:
     * the upload stores the photo and writes this address itself.
     */
    public static String validatePhotoUrl(String url, Long employeeId) {
        if (!StringUtils.hasText(url)) return null;
        String trimmed = url.trim();
        String own = PHOTO_PATH.formatted(employeeId);
        boolean ownPath = trimmed.equals(own)
            || (trimmed.startsWith(own + "?v=") && trimmed.substring(own.length() + 3).matches("\\d{1,18}"));
        if (!ownPath) {
            throw new BusinessRuleException(
                "INVALID_PHOTO_URL",
                "The profile photo cannot be set by address. Upload the photo instead.");
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
