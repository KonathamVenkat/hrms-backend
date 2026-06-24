package com.hrms.employee.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hrms.common.enums.BloodGroup;
import com.hrms.common.enums.EmploymentStatus;
import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import com.hrms.common.enums.MaritalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Full employee detail response DTO.
 *
 * <p>Returned by {@code GET /api/v1/employees/{id}} and used by the Angular
 * employee detail / edit page. Contains all persisted fields plus computed
 * display helpers (e.g., {@code fullName}, {@code fullNameAr}).</p>
 *
 * <p>Null fields are omitted from the JSON payload to keep responses lean
 * ({@link JsonInclude.Include#NON_NULL}).</p>
 */
@Data
@Builder
public class EmployeeResponse {
    private Long      id;
    private String    employeeCode;
    private String    firstName;
    private String    firstNameAr;
    private String    middleName;
    private String    middleNameAr;
    private String    lastName;
    private String    lastNameAr;
    private String    fullNameEn;        // ← ADD
    private String    fullNameAr;        // ← ADD
    private LocalDate dateOfBirth;
    private String    gender;
    private String    bloodGroup;
    private String    maritalStatus;
    private String    nationality;
    private String    religion;
    private String    profilePhotoUrl;
    private String    personalEmail;
    private String    workEmail;
    private String    personalPhone;
    private String    workPhone;
    private LocalDate hireDate;
    private LocalDate probationEndDate;
    private LocalDate confirmationDate;
    private String    employmentStatus;
    private String    employmentType;
    private Boolean   isActive;
}