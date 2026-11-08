package com.hrms.employee.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OfficeLocationRequest {

    @NotBlank(message = "Location code is required")
    @Size(max = 20)
    @Pattern(regexp = "^[A-Z0-9_]+$",
             message = "Code must be uppercase letters, numbers and underscores only")
    private String locationCode;

    @NotBlank(message = "Location name is required")
    @Size(max = 200)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Name contains control or invisible characters")
    private String locationName;

    @NotBlank(message = "Arabic location name is required")
    @Size(max = 200)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Arabic name contains control or invisible characters")
    private String locationNameAr;

    @NotBlank(message = "Location type is required")
    @Pattern(regexp = "^(HEAD_OFFICE|BRANCH|REMOTE|WAREHOUSE|SITE)$",
             message = "Type must be HEAD_OFFICE, BRANCH, REMOTE, WAREHOUSE or SITE")
    private String locationType;

    @NotBlank(message = "Address line 1 is required")
    @Size(max = 300)
    private String addressLine1;

    @Size(max = 300)
    private String addressLine2;

    @NotBlank(message = "City is required")
    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String stateProvince;

    @NotBlank(message = "Country is required")
    @Size(max = 100)
    private String country;

    @Size(max = 20)
    private String postalCode;

    @Size(max = 30)
    @Pattern(regexp = "^$|^[0-9+() -]{3,30}$", message = "Phone may contain digits, spaces, + ( ) and - only")
    private String phone;

    @Email(message = "Invalid email format")
    @Size(max = 200)
    private String email;

    @Size(max = 50)
    private String timezone;

    /** Not given (null) means the service default; anything else, an empty text included, must be a real time zone. */
    @AssertTrue(message = "Unknown time zone")
    public boolean isTimezoneKnown() {
        return timezone == null || java.time.ZoneId.getAvailableZoneIds().contains(timezone);
    }

    @DecimalMin(value = "-90",  message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90",   message = "Latitude must be between -90 and 90")
    private BigDecimal latitude;

    @DecimalMin(value = "-180", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180",  message = "Longitude must be between -180 and 180")
    private BigDecimal longitude;

    @Min(value = 0)
    private Integer sortOrder;

    private Boolean isActive;
    /** The version the screen was loaded with; required to update (400 when missing, 409 when stale). */
    private Long version;
}

