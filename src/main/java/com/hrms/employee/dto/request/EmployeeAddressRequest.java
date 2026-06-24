package com.hrms.employee.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EmployeeAddressRequest {

    @NotBlank(message = "Address type is required")
    @Pattern(regexp = "^(PERMANENT|CURRENT|EMERGENCY|MAILING)$",
             message = "Type must be PERMANENT, CURRENT, EMERGENCY or MAILING")
    private String addressType;

    @NotBlank(message = "Address line 1 is required")
    @Size(max = 300, message = "Address line 1 max 300 characters")
    private String addressLine1;

    @Size(max = 300, message = "Address line 2 max 300 characters")
    private String addressLine2;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City max 100 characters")
    private String city;

    @Size(max = 100, message = "State/Province max 100 characters")
    private String stateProvince;

    @NotBlank(message = "Country is required")
    @Size(max = 100, message = "Country max 100 characters")
    private String country;

    @Size(max = 20, message = "Postal code max 20 characters")
    private String postalCode;

    private Boolean isPrimary;
}
