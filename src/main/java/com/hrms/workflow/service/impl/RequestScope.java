package com.hrms.workflow.service.impl;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.workflow.entity.WorkflowDefinition;
import com.hrms.workflow.entity.WorkflowStep;
import com.hrms.workflow.enums.ApproverType;
import com.hrms.workflow.enums.RequestType;
import com.hrms.workflow.repository.RequestHistoryRepository;
import com.hrms.workflow.repository.WorkflowDefinitionRepository;
import com.hrms.workflow.repository.WorkflowRouteRepository;
import com.hrms.workflow.repository.WorkflowStepRepository;
import com.hrms.workflow.service.WorkflowRequestAccess;
import com.hrms.workflow.service.impl.StepEntry.Ctx;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads a request for an action or a read, and refuses an unrelated caller before anything about the request is
 * revealed (spec 10, IDOR). Related = the requester, an actor in its history, a holder of a step of its live flow,
 * or HR_ADMIN / HR_MANAGER / GENERAL_MANAGER. Everyone else gets the same 403 whether the request exists, is legacy,
 * closed or at another step; only those overseers may learn that an id does not exist (404).
 */
@Component
@RequiredArgsConstructor
class RequestScope {

    static final String NO_ACCESS = "You do not have access to this request.";

    private final WorkflowPorts ports;
    private final FlowEngine engine;
    private final WorkflowDefinitionRepository definitions;
    private final WorkflowStepRepository steps;
    private final WorkflowRouteRepository routes;
    private final RequestHistoryRepository historyRows;
    private final WorkflowAuthorization authorization;
    private final EmployeeAccessGuard accessGuard;

    /** The live step of a request: its context, the open Flowable task and that task's step row. */
    record Live(Ctx ctx, FlowEngine.OpenTask task, WorkflowStep step) {
    }

    /** Uniform 403 for an unrelated caller; otherwise the live step, empty when legacy or closed. */
    Optional<Live> related(RequestType type, long requestId) {
        WorkflowRequestAccess access = ports.access(type);
        boolean overseer = authorization.isOverseer();
        long requester = requesterOrDenied(access, requestId, overseer);
        Optional<Live> live = load(type, requestId, requester, access);
        if (overseer || isParticipant(type, requestId, requester, live)) {
            return live;
        }
        throw new AccessDeniedException(NO_ACCESS);
    }

    /**
     * Stricter than {@link #related}, for what only those who decide may see (the attached document): the requester,
     * an overseer, a holder of a live step that is not the department secretary's (who only forwards), or a past actor
     * at such a step. A caller involved only as secretary gets the same 403 as an unrelated one.
     */
    void requireDecider(RequestType type, long requestId) {
        if (!isDecider(type, requestId, related(type, requestId))) {
            throw new AccessDeniedException(NO_ACCESS);
        }
    }

    /** {@link #requireDecider} without throwing, for a caller already known to be related ({@code live} from it). */
    boolean isDecider(RequestType type, long requestId, Optional<Live> live) {
        if (authorization.isOverseer()) {
            return true;
        }
        WorkflowRequestAccess access = ports.access(type);
        long requester = access.requesterEmployeeId(requestId);
        Long caller = accessGuard.currentEmployeeId();
        if (caller != null && caller == requester) {
            return true;
        }
        boolean holdsDecidingStep = live.filter(l -> authorization.holdsAny(deciding(l.ctx().flow().steps().values()),
            requester, l.ctx().targetDepartmentId())).isPresent();
        return holdsDecidingStep || (caller != null && actedAtDecidingStep(type, requestId, caller, access));
    }

    private static List<WorkflowStep> deciding(Collection<WorkflowStep> all) {
        return all.stream().filter(s -> s.getApproverType() != ApproverType.DEPARTMENT_SECRETARY).toList();
    }

    /** Past actions count by the step's type in the flow version the request entered; unknown version = no. */
    private boolean actedAtDecidingStep(RequestType type, long requestId, long caller, WorkflowRequestAccess access) {
        Optional<Long> definition = access.definitionId(requestId);
        if (definition.isEmpty()) {
            return false;
        }
        Set<String> decidingKeys = deciding(steps.findByDefinitionIdOrderByPositionNo(definition.get())).stream()
            .map(WorkflowStep::getStepKey).collect(Collectors.toSet());
        return historyRows.findByRequestTypeAndRequestIdOrderByActedAtAscHistoryIdAsc(type, requestId).stream()
            .anyMatch(h -> Long.valueOf(caller).equals(h.getActorEmployeeId()) && decidingKeys.contains(h.getStepKey()));
    }

    /** For an action, after {@link #related}: the live step, else NOT_IN_WORKFLOW (legacy) or 409 (closed). */
    Live open(RequestType type, long requestId, Optional<Live> live) {
        if (live.isPresent()) {
            return live.get();
        }
        if (ports.access(type).processInstanceId(requestId).isEmpty()) {
            throw new BusinessRuleException("NOT_IN_WORKFLOW", "This request does not use the approval workflow.");
        }
        throw FlowEngine.alreadyHandled();
    }

    private long requesterOrDenied(WorkflowRequestAccess access, long requestId, boolean overseer) {
        try {
            return access.requesterEmployeeId(requestId);
        } catch (ResourceNotFoundException e) {
            if (overseer) {
                throw e;
            }
            throw new AccessDeniedException(NO_ACCESS);
        }
    }

    private boolean isParticipant(RequestType type, long requestId, long requester, Optional<Live> live) {
        Long caller = accessGuard.currentEmployeeId();
        if (caller == null) {
            return false;
        }
        return caller == requester
            || historyRows.existsByRequestTypeAndRequestIdAndActorEmployeeId(type, requestId, caller)
            || live.filter(l -> authorization.holdsAny(l.ctx().flow().steps().values(), requester,
                l.ctx().targetDepartmentId())).isPresent();
    }

    private Optional<Live> load(RequestType type, long requestId, long requester, WorkflowRequestAccess access) {
        Optional<String> pid = access.processInstanceId(requestId);
        Optional<FlowEngine.OpenTask> task = pid.flatMap(engine::openTask);
        if (task.isEmpty()) {
            return Optional.empty();
        }
        FlowEngine.OpenTask t = task.get();
        WorkflowDefinition def = definitions.findByFlowableProcessDefinitionId(t.processDefinitionId())
            .orElseThrow(() -> new IllegalStateException("No workflow definition for " + t.processDefinitionId()));
        LoadedFlow flow = LoadedFlow.load(def.getDefinitionId(), steps, routes);
        Ctx ctx = new Ctx(type, requestId, requester, access.targetDepartmentId(requestId), pid.get(), flow);
        return Optional.of(new Live(ctx, t, flow.step(t.key())));
    }
}
