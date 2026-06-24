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

    @Size(max = 20, message = "Job position ID max 20 characters")
    private String jobPositionId;

    private Long reportingManagerId;
    private Long functionalManagerId;

    @NotNull(message = "Location is required")
    private Long locationId;

    private Long shiftId;

    @NotBlank(message = "Work mode is required")
    @Pattern(regexp = "^(ON_SITE|REMOTE|HYBRID)$",
             message = "Work mode must be ON_SITE, REMOTE, or HYBRID")
    private String workMode;

    @NotNull(message = "Effective from date is required")
    private LocalDate effectiveFrom;

    @Size(max = 500, message = "Remarks max 500 characters")
    private String remarks;
}

