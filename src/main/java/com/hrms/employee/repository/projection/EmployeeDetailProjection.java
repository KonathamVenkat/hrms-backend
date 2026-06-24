package com.hrms.employee.repository.projection;

/**
 * Projection for the employee detail native query.
 * Joins EMPLOYEES + DEPARTMENTS + DESIGNATIONS in one query.
 * All getter names must match SQL column aliases exactly.
 */
public interface EmployeeDetailProjection {

    // ── EMPLOYEES columns ─────────────────────────────────────
    Long   getEmployeeId();
    String getEmployeeCode();
    String getFirstName();
    String getFirstNameAr();
    String getMiddleName();
    String getMiddleNameAr();
    String getLastName();
    String getLastNameAr();
    String getDateOfBirth();
    String getGender();
    String getBloodGroup();
    String getMaritalStatus();
    String getNationality();
    String getReligion();
    String getProfilePhotoUrl();
    String getPersonalEmail();
    String getWorkEmail();
    String getPersonalPhone();
    String getWorkPhone();
    String getHireDate();
    String getProbationEndDate();
    String getConfirmationDate();
    String getEmploymentStatus();
    String getEmploymentType();
    Integer getIsActive();
    String getCreatedBy();
    String getCreatedAt();
    String getUpdatedBy();
    String getUpdatedAt();

    // ── DEPARTMENTS columns ───────────────────────────────────
    Long   getDepartmentId();
    String getDepartmentName();
    String getDepartmentCode();
    String getDepartmentNameAr();

    // ── DESIGNATIONS columns ──────────────────────────────────
    Long   getDesignationId();
    String getDesignationTitle();
    String getDesignationTitleAr();
    String getDesignationCode();
    String getGradeLevel();
}