package com.hrms.employee.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class IdentityInfoRequest {

    @Size(max = 200, message = "National ID max 200 characters")
    private String nationalId;

    @Size(max = 200, message = "Passport number max 200 characters")
    private String passportNumber;

    @Size(max = 200, message = "Tax ID max 200 characters")
    private String taxId;

    @Size(max = 200, message = "Social security number max 200 characters")
    private String socialSecurityNumber;

    @Size(max = 100, message = "Driving license number max 100 characters")
    private String drivingLicenseNumber;

    @Size(max = 100, message = "Visa number max 100 characters")
    private String visaNumber;

    @Size(max = 50, message = "Visa type max 50 characters")
    private String visaType;

    private LocalDate visaIssueDate;

    private LocalDate visaExpiryDate;

    @Size(max = 100, message = "Work permit number max 100 characters")
    private String workPermitNumber;

    private LocalDate workPermitExpiry;

    @Size(max = 100, message = "Biometric ID max 100 characters")
    private String biometricId;
}

