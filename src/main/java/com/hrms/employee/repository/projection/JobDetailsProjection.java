package com.hrms.employee.repository.projection;

/**
 * Projection for EMPLOYEE_JOB_DETAILS native query.
 * Returns enriched job data with department, designation,
 * manager names, location and shift details.
 */
public interface JobDetailsProjection {

    // ── Core ──────────────────────────────────────────────────
    Long    getJobDetailsId();
    Long    getEmployeeId();

    // ── Department ────────────────────────────────────────────
    Long    getDepartmentId();
    String  getDepartmentName();
    String  getDepartmentNameAr();
    String  getDepartmentCode();

    // ── Designation ───────────────────────────────────────────
    Long    getDesignationId();
    String  getDesignationTitle();
    String  getDesignationTitleAr();
    String  getGradeLevel();

    // ── Job position ──────────────────────────────────────────
    String  getJobPositionId();

    // ── Reporting manager ─────────────────────────────────────
    Long    getReportingManagerId();
    String  getReportingManagerName();
    String  getReportingManagerCode();

    // ── Functional manager ────────────────────────────────────
    Long    getFunctionalManagerId();
    String  getFunctionalManagerName();
    String  getFunctionalManagerCode();

    // ── Location ──────────────────────────────────────────────
    Long    getLocationId();
    String  getLocationName();
    String  getLocationNameAr();
    String  getLocationCode();
    String  getLocationCity();

    // ── Shift ─────────────────────────────────────────────────
    Long    getShiftId();
    String  getShiftName();
    String  getShiftStartTime();
    String  getShiftEndTime();

    // ── Work mode & SCD ──────────────────────────────────────
    String  getWorkMode();
    String  getEffectiveFrom();
    String  getEffectiveTo();
    Integer getIsCurrent();
    String  getRemarks();
    String  getCreatedAt();
    String  getCreatedBy();
}
