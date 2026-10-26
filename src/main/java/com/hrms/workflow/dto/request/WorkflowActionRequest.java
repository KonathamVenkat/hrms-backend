package com.hrms.workflow.dto.request;

import com.hrms.workflow.enums.StepOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An approver's action on the current step. {@code stepKey} is the step the client saw: a different live step means
 * the request has moved on (HTTP 409). {@code targetDepartmentId} is read only on a step that picks the department.
 */
public record WorkflowActionRequest(
    @NotBlank(message = "stepKey is required") String stepKey,
    @NotNull(message = "outcome is required") StepOutcome outcome,
    Long targetDepartmentId,
    @Size(max = 500, message = "comment must be at most 500 characters") String comment) {
}
