package com.hrms.recruitment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RejectRequisitionRequest(

    @NotBlank(message = "A reason is required to reject a requisition") @Size(max = 500) String reason,

    @NotNull(message = "version is required") Long version
) { }
