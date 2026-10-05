package com.hrms.employee.dto.response;

import lombok.*;
import java.time.LocalDate;

@Data @Builder
public class IdentityInfoResponse {

    private Long      employeeIdentityId;
    private Long      employeeId;

    // National identity
    private String    nationalId;

    // Passport
    private String    passportNumber;

    // Tax & Social Security
    private String    taxId;
    private String    socialSecurityNumber;

    // Driving License
    private String    drivingLicenseNumber;

    // Visa
    private String    visaNumber;
    private String    visaType;
    private LocalDate visaIssueDate;
    private LocalDate visaExpiryDate;
    private Boolean   visaExpiringSoon;      // computed — within 60 days

    // Work Permit
    private String    workPermitNumber;
    private LocalDate workPermitExpiry;
    private Boolean   workPermitExpiringSoon; // computed — within 60 days

    // Biometric
    private String    biometricId;

    // True when the identifying numbers above are masked for this viewer (only the last 4
    // characters are shown). A masked response must not be used to prefill an edit form.
    private Boolean   masked;

    // Audit
    private Long      version;
    private String    createdBy;
    private String    createdAt;
    private String    updatedAt;
    private String    updatedBy;
}
