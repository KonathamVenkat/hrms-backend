package com.hrms.employee.dto.request;

import com.hrms.common.enums.Gender;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EmployeeDependentRequest {

    @NotNull(message = "Relation is required")
    private Long relationId;

    @NotBlank(message = "First name is required")
    @Size(max = 100)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "First name contains control or invisible characters")
    private String firstName;

    @Size(max = 100)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Middle name contains control or invisible characters")
    private String middleName;

    @Size(max = 100)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Last name contains control or invisible characters")
    private String lastName;

    @Size(max = 200)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Arabic first name contains control or invisible characters")
    private String firstNameAr;

    @Size(max = 200)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Arabic middle name contains control or invisible characters")
    private String middleNameAr;

    @Size(max = 200)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Arabic last name contains control or invisible characters")
    private String lastNameAr;

    private Gender gender;

    @PastOrPresent(message = "Date of birth cannot be in the future")
    private LocalDate dateOfBirth;

    @Size(max = 80)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Birth place contains control or invisible characters")
    private String birthPlace;

    /** Left out on add means true. */
    private Boolean isAlive;

    @Size(max = 30)
    @Pattern(regexp = "^[0-9+() -]*$", message = "Phone may contain only digits, + ( ) - and spaces")
    private String phone;

    @Size(max = 200)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Address contains control or invisible characters")
    private String address;

    /** The version the screen was loaded with. Required to update; ignored when adding. */
    private Long version;
}
