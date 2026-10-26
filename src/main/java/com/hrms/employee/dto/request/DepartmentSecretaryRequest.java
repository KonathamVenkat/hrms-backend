package com.hrms.employee.dto.request;

import jakarta.validation.constraints.NotNull;

/** Whether the employee should be the secretary of their current department. */
public record DepartmentSecretaryRequest(@NotNull(message = "secretary is required") Boolean secretary) {
}
