package com.hrms.employee.dto.response;

/** One branch with its location and manager, for the HR set-up screen. */
public record BranchPlacementResponse(
    Long branchId,
    String code,
    String name,
    String nameAr,
    Long companyId,
    Long locationId,
    String locationName,
    String locationNameAr,
    Long managerEmployeeId,
    String managerName,
    String managerCode,
    Boolean isActive,
    Long version) {
}
