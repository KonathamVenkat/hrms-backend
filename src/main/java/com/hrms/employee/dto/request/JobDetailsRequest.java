package com.hrms.employee.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;

/**
 * Request DTO for creating or updating employee job details.
 * Creating a new record automatically closes the previous one (SCD Type 2).
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class JobDetailsRequest {

    @NotNull(message = "Department is required")
    private Long departmentId;

    @NotNull(message = "Designation is required")
    private Long designationId;

    /** Optional: defaults to the designation's profession; when both are set they must be the same. */
    private Long professionId;

    /** Optional: the section of the chosen department the employee works in. */
    private Long sectionId;

    /** Optional: the specialization (a master) of the employee; it must be active when it is set. */
    private Long specializationId;

    /** Optional: contract type (a master) and the contract period; the end cannot be before the start. */
    private Long contractTypeId;
    private LocalDate contractStartDate;
    private LocalDate contractEndDate;

    @Size(max = 30, message = "Form request no. max 30 characters")
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Form request no. contains control or invisible characters")
    private String formRequestNo;

    @Size(max = 30, message = "Appointment request no. max 30 characters")
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Appointment request no. contains control or invisible characters")
    private String appointmentRequestNo;

    @Size(max = 30, message = "AFE max 30 characters")
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "AFE contains control or invisible characters")
    private String afe;

    @Size(max = 30, message = "Company ID card no. max 30 characters")
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Company ID card no. contains control or invisible characters")
    private String companyIdCardNo;

    @Size(max = 300, message = "Current work address max 300 characters")
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Current work address contains control or invisible characters")
    private String currentWorkAddress;

    @Size(max = 30, message = "Current work phone max 30 characters")
    @Pattern(regexp = "^[0-9+()\\- ]*$", message = "Current work phone may only hold digits, spaces and + ( ) -")
    private String currentWorkPhone;

    @Size(max = 20, message = "Job position ID max 20 characters")
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Job position ID contains control or invisible characters")
    private String jobPositionId;

    private Long reportingManagerId;
    private Long functionalManagerId;

    @NotNull(message = "Location is required")
    private Long locationId;

    private Long shiftId;

    /** Optional: the branch the employee belongs to; it must be active and in the chosen location. */
    private Long companyBranchId;

    @NotBlank(message = "Work mode is required")
    @Pattern(regexp = "^(ON_SITE|REMOTE|HYBRID)$",
             message = "Work mode must be ON_SITE, REMOTE, or HYBRID")
    private String workMode;

    @NotNull(message = "Effective from date is required")
    private LocalDate effectiveFrom;

    @Size(max = 500, message = "Remarks max 500 characters")
    @Pattern(regexp = "^(?:[\r\n\t]|[^\\p{Cc}\\p{Cf}])*$", message = "Remarks contain control or invisible characters")
    private String remarks;

    /** The version the screen was loaded with; required when updating the current job, ignored when assigning a new one. */
    private Long version;
}

