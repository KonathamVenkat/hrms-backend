package com.hrms.workflow.dto.request;

import com.hrms.workflow.enums.StepOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** HR_ADMIN acting on a person step that has nobody to act on it. The reason is mandatory and kept in the history. */
public record WorkflowOverrideRequest(
    @NotBlank(message = "stepKey is required") String stepKey,
    @NotNull(message = "outcome is required") StepOutcome outcome,
    Long targetDepartmentId,
    @NotBlank(message = "reason is required")
    @Size(max = 500, message = "reason must be at most 500 characters") String reason) {
}
