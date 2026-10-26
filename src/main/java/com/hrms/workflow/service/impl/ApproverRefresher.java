package com.hrms.workflow.service.impl;

import com.hrms.employee.service.OrganisationChanged;
import com.hrms.workflow.entity.WorkflowStep;
import com.hrms.workflow.repository.WorkflowStepRepository;
import com.hrms.workflow.service.WorkflowRequestAccess;
import com.hrms.workflow.service.WorkflowRequestAccess.OpenRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Keeps the stored current approver of waiting requests in step with the organisation: when a head, secretary or
 * branch manager is replaced (event from the employee module, same transaction) every waiting person-step request is
 * re-resolved, so the new holder finds it in the inbox and the old one no longer does. Authorization never depended
 * on the stored value; this only fixes who is shown the item. The set of waiting requests is small (in-flight only).
 */
@Component
@RequiredArgsConstructor
class ApproverRefresher {

    private final WorkflowPorts ports;
    private final WorkflowStepRepository steps;
    private final ApproverResolver resolver;

    @EventListener
    void onOrganisationChanged(OrganisationChanged event) {
        Map<Long, Map<String, WorkflowStep>> flows = new HashMap<>();
        for (WorkflowRequestAccess access : ports.allAccess()) {
            for (OpenRequest r : access.openRequests()) {
                if (r.definitionId() == null) {
                    continue;
                }
                WorkflowStep step = flows.computeIfAbsent(r.definitionId(), this::stepsOf).get(r.stepKey());
                if (step == null) {
                    continue;
                }
                StoredApprover now = StoredApprover.of(step,
                    resolver.resolve(step, r.requesterEmployeeId(), r.targetDepartmentId()).orElse(null),
                    r.requesterEmployeeId());
                if (!Objects.equals(now.employeeId(), r.approverEmployeeId())
                        || !Objects.equals(now.role(), r.approverRole())) {
                    access.markStage(r.requestId(), r.stage(), r.stepKey(), step.getApproverType().name(),
                        now.employeeId(), now.role());
                }
            }
        }
    }

    private Map<String, WorkflowStep> stepsOf(Long definitionId) {
        Map<String, WorkflowStep> byKey = new HashMap<>();
        steps.findByDefinitionIdOrderByPositionNo(definitionId).forEach(s -> byKey.put(s.getStepKey(), s));
        return byKey;
    }
}
