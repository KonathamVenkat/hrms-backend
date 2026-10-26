package com.hrms.workflow.dto.response;

import com.hrms.workflow.enums.RequestType;
import com.hrms.workflow.enums.StepOutcome;

import java.util.List;

/**
 * Where a request stands. {@code stage} is PENDING_&lt;STEP_KEY&gt; while open, else APPROVED / REJECTED / CANCELLED;
 * the step fields are null and {@code allowedOutcomes} empty once the request is closed. {@code allowedOutcomes} and
 * {@code pickTargetDepartment} are what the CALLER may do now (empty for anyone who cannot act). The requester id,
 * name and one-line summary are for the detail page; name and summary may be null.
 */
public record WorkflowStatusResponse(
    RequestType type,
    long requestId,
    String stage,
    String stepKey,
    String stepLabelEn,
    String stepLabelAr,
    List<StepOutcome> allowedOutcomes,
    boolean pickTargetDepartment,
    long requesterEmployeeId,
    String requesterName,
    String summary) {
}
