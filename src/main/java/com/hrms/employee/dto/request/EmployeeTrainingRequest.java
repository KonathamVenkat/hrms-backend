package com.hrms.employee.dto.request;

import com.hrms.common.enums.Gender;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EmployeeTrainingRequest {

    @NotBlank(message = "Course name is required")
    @Size(max = 200)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Course name contains control or invisible characters")
    private String courseName;

    @Size(max = 200)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Arabic course name contains control or invisible characters")
    private String courseNameAr;

    @Size(max = 200)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Provider contains control or invisible characters")
    private String provider;

    private LocalDate startDate;

    private LocalDate endDate;

    /** The version the screen was loaded with. Required to update; ignored when adding. */
    private Long version;
}
