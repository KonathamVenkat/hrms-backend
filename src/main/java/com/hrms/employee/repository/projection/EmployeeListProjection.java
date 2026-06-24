package com.hrms.employee.repository.projection;

public interface EmployeeListProjection {
	 
    // ── From EMPLOYEES table ──────────────────────────────────
    Long    getEmployeeId();
    String  getEmployeeCode();
    String  getFirstName();
    String  getLastName();
    String  getMiddleName();
    String  getFirstNameAr();
    String  getLastNameAr();
    String  getGender();
    String  getWorkEmail();
    String  getWorkPhone();
    String  getPersonalPhone();
    String  getProfilePhotoUrl();
    String  getEmploymentStatus();
    String  getEmploymentType();
    String  getNationality();
    String  getHireDate();
    Integer getIsActive();
 
    // ── From DEPARTMENTS table (LEFT JOIN) ────────────────────
    Long    getDepartmentId();
    String  getDepartmentName();
    String  getDepartmentCode();
 
    // ── From DESIGNATIONS via EMPLOYEE_JOB_DETAILS ────────────
    Long    getDesignationId();
    String  getDesignationTitle();
    String  getGradeLevel();
}