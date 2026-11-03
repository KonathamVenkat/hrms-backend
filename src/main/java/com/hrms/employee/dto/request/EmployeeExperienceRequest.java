package com.hrms.employee.dto.request;

import com.hrms.common.enums.Gender;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EmployeeExperienceRequest {

    @NotBlank(message = "Employer is required")
    @Size(max = 200)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Employer contains control or invisible characters")
    private String employerName;

    @Size(max = 200)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Arabic employer name contains control or invisible characters")
    private String employerNameAr;

    @Size(max = 100)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Job title contains control or invisible characters")
    private String jobTitle;

    @Size(max = 100)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Place contains control or invisible characters")
    private String place;

    @Size(max = 300, message = "Work address max 300 characters")
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Work address contains control or invisible characters")
    private String workAddress;

    @Size(max = 30, message = "Work phone max 30 characters")
    @Pattern(regexp = "^[0-9+()\\- ]*$", message = "Work phone may only hold digits, spaces and + ( ) -")
    private String workPhone;

    private LocalDate startDate;

    private LocalDate endDate;

    /** The version the screen was loaded with. Required to update; ignored when adding. */
    private Long version;
}
