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

    @Size(max = 100)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Fourth name contains control or invisible characters")
    private String fourthName;

    @Size(max = 200)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Arabic fourth name contains control or invisible characters")
    private String fourthNameAr;

    @NotBlank(message = "Last name is required")
    @Size(max = 100)
    private String lastName;

    @NotBlank(message = "Arabic last name is required")
    @Size(max = 200)
    private String lastNameAr;

    @Size(max = 200, message = "Place of birth max 200 characters")
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Place of birth contains control or invisible characters")
    private String placeOfBirth;

    @Size(max = 1000, message = "Notes max 1000 characters")
    @Pattern(regexp = "^[\\p{L}\\p{M}\\p{N}\\p{P}\\p{S}\\p{Zs}\\n\\r]*$", message = "Notes contains control or invisible characters")
    private String notes;

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

    /** The version the screen was loaded with. Required: a missing or stale version is refused (409). */
    private Long version;
}
