package com.hrms.employee.dto.response;

import lombok.*;

/**
 * Full enriched response for a single job details record.
 * Includes all joined names for display without additional queries.
 */
@Data @Builder
public class JobDetailsResponse {

    private Long    jobDetailsId;
    private Long    employeeId;

    // Department
    private Long    departmentId;
    private String  departmentName;
    private String  departmentNameAr;
    private String  departmentCode;

    // Designation
    private Long    designationId;
    private String  designationTitle;
    private String  designationTitleAr;
    private String  gradeLevel;

    // Job position
    private String  jobPositionId;

    // Reporting manager
    private Long    reportingManagerId;
    private String  reportingManagerName;
    private String  reportingManagerCode;

    // Functional manager
    private Long    functionalManagerId;
    private String  functionalManagerName;
    private String  functionalManagerCode;

    // Location
    private Long    locationId;
    private String  locationName;
    private String  locationNameAr;
    private String  locationCode;
    private String  locationCity;

    // Shift
    private Long    shiftId;
    private String  shiftName;
    private String  shiftStartTime;
    private String  shiftEndTime;

    // Work details
    private String  workMode;
    private String  effectiveFrom;
    private String  effectiveTo;
    private Boolean isCurrent;
    private String  remarks;

    // Audit
    private String  createdBy;
    private String  createdAt;
}
