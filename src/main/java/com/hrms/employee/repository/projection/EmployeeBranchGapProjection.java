package com.hrms.employee.repository.projection;

/** One active employee whose current job row has no branch yet (the back-fill list). */
public interface EmployeeBranchGapProjection {
    Long   getEmployeeId();
    String getEmployeeCode();
    String getFirstName();
    String getLastName();
    String getFirstNameAr();
    String getLastNameAr();
    String getDepartmentName();
    Long   getLocationId();
    String getLocationName();
}
