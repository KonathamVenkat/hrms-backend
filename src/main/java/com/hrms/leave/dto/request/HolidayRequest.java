package com.hrms.leave.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class HolidayRequest {

    @NotBlank(message = "Holiday name is required")
    @Size(max = 200, message = "Name max 200 characters")
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Name contains control or invisible characters")
    private String holidayName;

    @NotBlank(message = "Arabic holiday name is required")
    @Size(max = 200, message = "Arabic name max 200 characters")
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Arabic name contains control or invisible characters")
    private String holidayNameAr;

    @NotNull(message = "Holiday date is required")
    private LocalDate holidayDate;

    @NotBlank(message = "Holiday type is required")
    @Pattern(
        regexp = "^(PUBLIC|RELIGIOUS|OPTIONAL|RESTRICTED)$",
        message = "Type must be PUBLIC, RELIGIOUS, OPTIONAL, or RESTRICTED"
    )
    private String holidayType;

    @Size(max = 500, message = "Description max 500 characters")
    private String description;

    private Boolean isRecurring;
    private Boolean isActive;
    /** The version the screen was loaded with; required to update (400 when missing, 409 when stale). */
    private Long version;
}
