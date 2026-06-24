package com.hrms.employee.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmployeeSummaryResponse {
    private Long   employeeId;       // maps from e.id
    private String employeeCode;
    private String fullNameEn;       // firstName + middleName + lastName
    private String firstNameAr;
    private String lastNameAr;
    private String gender;
    private String workEmail;
    private String workPhone;
    private String profilePhotoUrl;
    private String employmentStatus;
    private String employmentType;
    private String nationality;
    private String hireDate;
    private Boolean isActive;
    private Long   departmentId;      // was missing
    private String departmentName;    // ← NEW
    private String departmentCode;    // ← NEW
    private Long   designationId;     // ← NEW
    private String designationTitle;  // ← NEW
    private String gradeLevel;        // ← NEW
}