package com.hrms.employee.dto.response;

import java.util.List;

/** How many rows were applied, and every row that was not, with a machine-readable reason. */
public record BranchAssignmentResult(int assigned, List<Skipped> skipped) {

    public record Skipped(Long employeeId, String reason) {
    }
}
