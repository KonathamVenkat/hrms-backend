package com.hrms.employee.dto.request;

import com.hrms.common.enums.Gender;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EmployeeEducationRequest {

    private Long educationLevelId;

    @NotBlank(message = "Qualification is required")
    @Size(max = 200)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Qualification contains control or invisible characters")
    private String qualification;

    @Size(max = 200)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Arabic qualification contains control or invisible characters")
    private String qualificationAr;

    @Size(max = 200)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Institution contains control or invisible characters")
    private String institution;

    private LocalDate startDate;

    private LocalDate endDate;

    /** The version the screen was loaded with. Required to update; ignored when adding. */
    private Long version;
}
