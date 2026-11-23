package com.hrms.workflow.dto.response;

import com.hrms.workflow.enums.RequestType;
import com.hrms.workflow.enums.StepOutcome;

import java.util.List;
import java.util.Map;

/**
 * Where a request stands. {@code stage} is PENDING_&lt;STEP_KEY&gt; while open, else APPROVED / REJECTED / CANCELLED;
 * the step fields are null and {@code allowedOutcomes} empty once the request is closed. {@code allowedOutcomes} and
 * {@code pickTargetDepartment} are what the CALLER may do now (empty for anyone who cannot act). The requester id,
 * name and one-line summary are for the detail page; name and summary may be null.
 * {@code mayOverride}: the CALLER (an HR_ADMIN, not the requester) may override the open step because nobody else can
 * act on it. {@code overridePicksTargetDepartment}: the open step chooses the department, so an override that forwards
 * must send one (same for every caller). Both false once closed.
 * {@code suggestedTargetDepartmentId}: when the CALLER picks the department, the requester's own department, to start
 * the choice from (null otherwise).
 * {@code mayDownloadAttachment}: the CALLER may open the request's attachment (those who decide, not the department
 * secretary); false when there is none.
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
    String summary,
    boolean mayOverride,
    boolean overridePicksTargetDepartment,
    Map<String, Object> summaryDetails,
    Long suggestedTargetDepartmentId,
    boolean mayDownloadAttachment) {

    /** Without details (the summary line only). */
    public WorkflowStatusResponse(RequestType type, long requestId, String stage, String stepKey, String stepLabelEn,
                                  String stepLabelAr, List<StepOutcome> allowedOutcomes, boolean pickTargetDepartment,
                                  long requesterEmployeeId, String requesterName, String summary, boolean mayOverride,
                                  boolean overridePicksTargetDepartment) {
        this(type, requestId, stage, stepKey, stepLabelEn, stepLabelAr, allowedOutcomes, pickTargetDepartment,
            requesterEmployeeId, requesterName, summary, mayOverride, overridePicksTargetDepartment, Map.of(), null, false);
    }
}
