package com.hrms.recruitment.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The approver confirms, on the record, that the budget was checked with Finance and Management agreed.
 * Both flags must be true; the database refuses an approval row without them as well.
 */
public record ApproveRequisitionRequest(

    @AssertTrue(message = "Confirm that the budget was checked with Finance") boolean budgetConfirmed,
    @AssertTrue(message = "Confirm that Management agreed") boolean managementConfirmed,

    @Size(max = 500) String comments,

    @NotNull(message = "version is required") Long version
) { }
