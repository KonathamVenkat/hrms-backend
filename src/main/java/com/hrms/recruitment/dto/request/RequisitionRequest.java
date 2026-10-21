package com.hrms.recruitment.dto.request;

import com.hrms.common.enums.EmploymentType;
import com.hrms.recruitment.enums.RequisitionPriority;
import com.hrms.recruitment.enums.RequisitionType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Create or update a requisition. It deliberately has no status, code, requester or currency: the server sets those.
 * {@code version} is required to update (HTTP 400 when missing, 409 when stale).
 */
public record RequisitionRequest(

    @NotBlank(message = "Job title is required")
    @Size(max = 200)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Job title contains control or invisible characters")
    String jobTitle,

    @Size(max = 300)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Arabic job title contains control or invisible characters")
    String jobTitleAr,

    @NotNull(message = "Department is required") @Positive Long departmentId,
    @NotNull(message = "Designation is required") @Positive Long designationId,
    @Positive Long locationId,
    @Positive Long reportingManagerId,

    @NotNull(message = "Number of positions is required") @Min(1) @Max(999) Integer noOfPositions,
    @NotNull(message = "Employment type is required") EmploymentType employmentType,
    @NotNull(message = "Requisition type is required") RequisitionType requisitionType,
    @Positive Long replacedEmployeeId,

    @NotNull(message = "Minimum experience is required")
    @DecimalMin("0.0") @DecimalMax("99.9") @Digits(integer = 2, fraction = 1) BigDecimal minExperienceYears,
    @DecimalMin("0.0") @DecimalMax("99.9") @Digits(integer = 2, fraction = 1) BigDecimal maxExperienceYears,

    @DecimalMin("0.000") @Digits(integer = 11, fraction = 3) BigDecimal minSalary,
    @DecimalMin("0.000") @Digits(integer = 11, fraction = 3) BigDecimal maxSalary,

    @NotNull(message = "Priority is required") RequisitionPriority priority,
    LocalDate targetJoinDate,

    // Tabs, line breaks and ordinary text are allowed; other control and invisible characters are not.
    @Size(max = 1000)
    @Pattern(regexp = "^[^\\p{Cf}\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F-\\x9F]*$",
             message = "Justification contains control or invisible characters")
    String justification,

    Long version
) { }
