package com.hrms.workflow.service.impl;

import com.hrms.common.enums.UserRole;
import com.hrms.workflow.entity.WorkflowStep;
import com.hrms.workflow.enums.ApproverType;

/**
 * What the request row stores as its current approver, so the inbox is one indexed query: the person of a person
 * step, or the role of a role step. A person step with nobody (or only the requester) is stored as HR_ADMIN, the one
 * role that may override it, so it is never in nobody's inbox. Display and routing only; authorization is live.
 */
record StoredApprover(Long employeeId, String role) {

    static final String OVERRIDE_ROLE = UserRole.HR_ADMIN.name();

    /** {@code holdingRole} is the role that holds a ROLE step for this requester (see ApproverResolver#roleHolding). */
    static StoredApprover of(WorkflowStep step, Long resolvedEmployeeId, long requesterId, String holdingRole) {
        if (step.getApproverType() == ApproverType.ROLE) {
            return new StoredApprover(null, holdingRole != null ? holdingRole : step.getApproverRole());
        }
        boolean someoneElse = resolvedEmployeeId != null && resolvedEmployeeId != requesterId;
        return someoneElse ? new StoredApprover(resolvedEmployeeId, null) : new StoredApprover(null, OVERRIDE_ROLE);
    }
}
