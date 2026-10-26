package com.hrms.workflow.service;

import com.hrms.workflow.enums.RequestType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The workflow's view of one request module's table (implemented once per request type, leave in Task 8), so the
 * workflow module never imports request repositories. Every write joins the caller's transaction.
 * {@code approverType} is an ApproverType name; {@code approverEmployeeId} is the resolved person of a person step
 * (a display snapshot, never used for authorization); {@code approverRole} is set for ROLE steps only.
 */
public interface WorkflowRequestAccess {

    RequestType type();

    /** @throws com.hrms.common.exception.ResourceNotFoundException if the request does not exist */
    long requesterEmployeeId(long requestId);

    Long targetDepartmentId(long requestId);

    /** Who asked and a one-line description, for the status response (name and summary may be null). */
    Brief brief(long requestId);

    record Brief(long requesterEmployeeId, String requesterName, String summary) {
    }

    /** The request's workflow stage (PENDING_..., APPROVED, ...); empty = a legacy request that never entered the flow. */
    Optional<String> stage(long requestId);

    /** Empty = a legacy request that never entered the flow. */
    Optional<String> processInstanceId(long requestId);

    /**
     * Called on submit, and again whenever a step picks the target department (same definition and instance, the
     * new department), so all workflow columns are written together.
     */
    void markEntered(long requestId, Long definitionId, String processInstanceId, Long targetDepartmentId,
                     String stage, String stepKey, String approverType, Long approverEmployeeId, String approverRole);

    void markStage(long requestId, String stage, String stepKey, String approverType, Long approverEmployeeId,
                   String approverRole);

    /**
     * One chunk of the requests waiting at one of {@code stages} (PENDING_&lt;STEP_KEY&gt;): at most {@code limit},
     * request id above {@code afterRequestId}, in id order. For re-pointing stored approvers; the caller pages on.
     */
    List<OpenRequest> openRequests(Collection<String> stages, long afterRequestId, int limit);

    record OpenRequest(long requestId, long requesterEmployeeId, Long targetDepartmentId, Long definitionId,
                       String stepKey, String stage, Long approverEmployeeId, String approverRole) {
    }

    /** APPROVED / REJECTED / CANCELLED; clears the current-step columns. */
    void markTerminal(long requestId, String stage);
}
