package com.hrms.employee.dto.request;

import jakarta.validation.constraints.NotNull;

/** Where a branch sits and who manages it. A null manager clears the manager. */
public record BranchPlacementRequest(
    @NotNull(message = "Location is required") Long locationId,
    Long managerEmployeeId,
    @NotNull(message = "Version is required") Long version) {
}
