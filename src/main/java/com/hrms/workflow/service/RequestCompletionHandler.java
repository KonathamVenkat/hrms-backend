package com.hrms.workflow.service;

import com.hrms.workflow.enums.RequestType;

/**
 * Downstream effects of a finished flow, implemented once per request module. Runs inside the workflow action's
 * transaction: an exception rolls the whole action back and the request stays at its step.
 */
public interface RequestCompletionHandler {

    RequestType type();

    /** HR processed: apply downstream effects, same transaction. */
    void onApproved(long requestId);

    void onRejected(long requestId, String reason);

    void onCancelled(long requestId);
}
