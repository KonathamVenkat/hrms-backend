package com.hrms.employee.dto.request;

import com.hrms.common.enums.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;

/**
 * Request DTO for updating an existing employee.
 * Employee code and work email are NOT updatable.
 * Auth user password and username are NOT updated here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEmployeeRequest {

    // ── Personal Info ─────────────────────────────────────────
    @NotBlank(message = "First name is required")
    @Size(max = 100)
    private String firstName;

    @NotBlank(message = "Arabic first name is required")
    @Size(max = 200)
    private String firstNameAr;

    @Size(max = 100)
    private String middleName;

    @Size(max = 200)
    private String middleNameAr;

    @NotBlank(message = "Last name is required")
    @Size(max = 100)
    private String lastName;

    @NotBlank(message = "Arabic last name is required")
    @Size(max = 200)
    private String lastNameAr;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @NotNull(message = "Gender is required")
    private Gender gender;

    private BloodGroup    bloodGroup;
    private MaritalStatus maritalStatus;

    @Size(max = 100)
    private String nationality;

    @Size(max = 100)
    private String religion;

    @Size(max = 500)
    private String profilePhotoUrl;

    // ── Contact ───────────────────────────────────────────────
    @NotBlank(message = "Personal email is required")
    @Email
    @Size(max = 200)
    private String personalEmail;

    // workEmail is NOT included — cannot be changed after creation

    @Size(max = 30)
    private String personalPhone;

    @Size(max = 30)
    private String workPhone;

    // ── Employment ────────────────────────────────────────────
    @NotNull(message = "Hire date is required")
    private LocalDate hireDate;

    private LocalDate probationEndDate;
    private LocalDate confirmationDate;

    @NotNull(message = "Employment type is required")
    private EmploymentType employmentType;

    @NotNull(message = "Employment status is required")
    private EmploymentStatus employmentStatus;

   
    private Long departmentId;

    private Long designationId;

    // ── Auth User role (updatable) ────────────────────────────
    @Pattern(regexp = "^(HR_ADMIN|HR_MANAGER|EMPLOYEE)$",
             message = "Role must be HR_ADMIN, HR_MANAGER, or EMPLOYEE")
    private String role;

    /** The version the screen was loaded with; omit to skip the stale-edit check. */
    private Long version;
}
