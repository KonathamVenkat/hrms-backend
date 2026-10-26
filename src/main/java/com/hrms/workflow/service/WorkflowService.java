package com.hrms.workflow.service;

import com.hrms.employee.dto.response.DepartmentLookupResponse;
import com.hrms.workflow.dto.request.WorkflowActionRequest;
import com.hrms.workflow.dto.request.WorkflowOverrideRequest;
import com.hrms.workflow.dto.response.RequestHistoryResponse;
import com.hrms.workflow.dto.response.WorkflowStatusResponse;
import com.hrms.workflow.enums.RequestType;

import java.util.List;
import java.util.Map;

/**
 * The only workflow API other modules call. Each action is one transaction (request columns through
 * {@link WorkflowRequestAccess}, a history row, the Flowable task, the completion handler). The caller's identity
 * always comes from the security context.
 */
public interface WorkflowService {

    /** Whether the per-type switch is on; a request module starts the flow only then and otherwise behaves as before. */
    boolean isEnabled(RequestType type);

    /**
     * Starts the flow for a saved request. Call it exactly once per newly inserted request row, inside the same
     * transaction as the insert (a second call is refused with 409, but two concurrent calls are not locked here).
     * {@code facts} are exactly the request type's condition fields, e.g. totalDays.
     */
    WorkflowStatusResponse submit(RequestType type, long requestId, long requesterEmployeeId,
                                  Long targetDepartmentId, Map<String, Number> facts);

    WorkflowStatusResponse act(RequestType type, long requestId, WorkflowActionRequest action);

    /** The requester only, while the request is pending. */
    WorkflowStatusResponse cancel(RequestType type, long requestId);

    /** HR_ADMIN only, on a person step with nobody (other than the requester) to act on it. */
    WorkflowStatusResponse override(RequestType type, long requestId, WorkflowOverrideRequest action);

    /**
     * Where the request stands, for the same readers as {@link #history}; 403 for anyone else, and a request that
     * never entered the flow is refused with NOT_IN_WORKFLOW.
     */
    WorkflowStatusResponse status(RequestType type, long requestId);

    /** The active departments a holder of a department-picking step may choose from; 403 for anyone else. */
    List<DepartmentLookupResponse> targetDepartments(RequestType type, long requestId);

    /**
     * Oldest first. Includes the participant check of spec 10 (requester, past actor, holder of a live step, or
     * HR_ADMIN / HR_MANAGER / GENERAL_MANAGER; anyone else 403), so the HTTP layer needs no extra check.
     */
    List<RequestHistoryResponse> history(RequestType type, long requestId);
}
