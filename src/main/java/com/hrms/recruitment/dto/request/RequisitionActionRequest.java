package com.hrms.recruitment.dto.request;

import jakarta.validation.constraints.NotNull;

/** Body of submit, hold, resume, close and cancel: the version the screen showed, so a stale click is refused. */
public record RequisitionActionRequest(@NotNull(message = "version is required") Long version) { }
