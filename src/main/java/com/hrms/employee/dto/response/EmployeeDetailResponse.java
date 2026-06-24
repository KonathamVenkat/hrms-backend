package com.hrms.employee.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Full employee detail response — used by the Angular view/detail page.
 * Includes data from EMPLOYEES + DEPARTMENTS + DESIGNATIONS tables.
 */
@Data
@Builder
public class EmployeeDetailResponse {

    // ── Identity ──────────────────────────────────────────────
    private Long   employeeId;
    private String employeeCode;

    // ── Name ─────────────────────────────────────────────────
    private String firstName;
    private String firstNameAr;
    private String middleName;
    private String middleNameAr;
    private String lastName;
    private String lastNameAr;
    private String fullNameEn;
    private String fullNameAr;

    // ── Personal ──────────────────────────────────────────────
    private String    dateOfBirth;
    private String    gender;
    private String    bloodGroup;
    private String    maritalStatus;
    private String    nationality;
    private String    religion;
    private String    profilePhotoUrl;

    // ── Contact ───────────────────────────────────────────────
    private String personalEmail;
    private String workEmail;
    private String personalPhone;
    private String workPhone;

    // ── Employment ────────────────────────────────────────────
    private String  hireDate;
    private String  probationEndDate;
    private String  confirmationDate;
    private String  employmentStatus;
    private String  employmentType;
    private Boolean isActive;

    // ── Department ────────────────────────────────────────────
    private Long   departmentId;
    private String departmentName;
    private String departmentCode;
    private String departmentNameAr;

    // ── Designation ───────────────────────────────────────────
    private Long   designationId;
    private String designationTitle;
    private String designationTitleAr;
    private String designationCode;
    private String gradeLevel;

    // ── Audit ─────────────────────────────────────────────────
    private String createdBy;
    private String createdAt;
    private String updatedBy;
    private String updatedAt;
}