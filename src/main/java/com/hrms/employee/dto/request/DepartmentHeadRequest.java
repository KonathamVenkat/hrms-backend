package com.hrms.employee.dto.request;

import jakarta.validation.constraints.NotNull;

/** Whether the employee should be the head of their current department. */
public record DepartmentHeadRequest(@NotNull(message = "head is required") Boolean head) {
}
