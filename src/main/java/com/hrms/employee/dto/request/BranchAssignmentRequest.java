package com.hrms.employee.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Back-fill: gives employees who have no branch yet their branch. At most 200 rows per call. */
public record BranchAssignmentRequest(
    @NotEmpty(message = "At least one assignment is required")
    @Size(max = 200, message = "At most 200 assignments per request")
    List<@Valid @NotNull Item> items) {

    public record Item(
        @NotNull(message = "Employee is required") Long employeeId,
        @NotNull(message = "Branch is required") Long branchId) {
    }
}
