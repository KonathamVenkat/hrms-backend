package com.hrms.workflow.service.impl;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.employee.dto.response.DepartmentLookupResponse;
import com.hrms.employee.service.DepartmentService;
import com.hrms.workflow.dto.request.WorkflowActionRequest;
import com.hrms.workflow.dto.request.WorkflowOverrideRequest;
import com.hrms.workflow.dto.response.RequestHistoryResponse;
import com.hrms.workflow.dto.response.WorkflowStatusResponse;
import com.hrms.workflow.entity.WorkflowDefinition;
import com.hrms.workflow.entity.WorkflowStep;
import com.hrms.workflow.enums.ApproverType;
import com.hrms.workflow.enums.HistoryAction;
import com.hrms.workflow.enums.RequestType;
import com.hrms.workflow.enums.StepOutcome;
import com.hrms.workflow.repository.WorkflowRouteRepository;
import com.hrms.workflow.repository.WorkflowStepRepository;
import com.hrms.workflow.service.WorkflowDefinitionService;
import com.hrms.workflow.service.WorkflowRequestAccess;
import com.hrms.workflow.service.WorkflowService;
import com.hrms.workflow.service.impl.RequestScope.Live;
import com.hrms.workflow.service.impl.StepEntry.Ctx;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The workflow state machine (spec 5.2, 7). Each public method is one transaction: request columns via the port, one
 * history row per transition, the Flowable task, the completion handler and the audit row commit or roll back
 * together. The step that is authorized is always the live Flowable task's; the client's stepKey only detects
 * staleness (409).
 */
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    static final String PENDING_PREFIX = "PENDING_";
    static final String CANCELLED = "CANCELLED";
    private static final Map<StepOutcome, HistoryAction> ACTION_OF = Map.of(StepOutcome.FORWARD,
        HistoryAction.FORWARDED, StepOutcome.APPROVE, HistoryAction.APPROVED, StepOutcome.REJECT, HistoryAction.REJECTED);

    private final WorkflowPorts ports;
    private final FlowEngine engine;
    private final StepEntry stepEntry;
    private final HistoryWriter history;
    private final WorkflowAuthorization authorization;
    private final WorkflowInputs inputs;
    private final EmployeeAccessGuard accessGuard;
    private final WorkflowDefinitionService definitionService;
    private final RequestScope scope;
    private final WorkflowStepRepository steps;
    private final WorkflowRouteRepository routes;
    private final AuditTrail audit;
    private final ApproverResolver resolver;
    private final DepartmentService departments;

    @Override
    @Transactional(readOnly = true)
    public boolean isEnabled(RequestType type) {
        return inputs.isEnabled(type);
    }

    @Override
    @Transactional
    public WorkflowStatusResponse submit(RequestType type, long requestId, long requesterEmployeeId,
                                         Long targetDepartmentId, Map<String, Number> facts) {
        inputs.requireEnabled(type);
        WorkflowRequestAccess access = ports.access(type);
        if (access.requesterEmployeeId(requestId) != requesterEmployeeId) {
            throw new IllegalArgumentException("Requester does not match request " + type + " " + requestId);
        }
        if (access.processInstanceId(requestId).isPresent()) {
            throw FlowEngine.alreadyHandled();
        }
        Map<String, Object> variables = WorkflowInputs.variables(type, facts);
        WorkflowDefinition def = definitionService.active(type).orElseThrow(() ->
            new BusinessRuleException("WORKFLOW_NOT_PUBLISHED", "No workflow is published for " + type + "."));
        LoadedFlow flow = LoadedFlow.load(def.getDefinitionId(), steps, routes);
        Long target = applicantTarget(flow, requesterEmployeeId, targetDepartmentId);
        if (target != null) {
            inputs.requireActiveDepartment(target);
        }
        String pid = engine.start(def.getFlowableProcessDefinitionId(), type + ":" + requestId, variables);
        history.byCaller(type, requestId, null, HistoryAction.SUBMITTED, null, null);
        Ctx ctx = new Ctx(type, requestId, requesterEmployeeId, target, pid, flow);
        if (!(stepEntry.enter(ctx) instanceof StepEntry.Rest rest)) {
            throw new BusinessRuleException("WORKFLOW_INVALID", "The workflow ended without a decision.");
        }
        return land(ctx, rest, true);
    }

    /**
     * The applicant's department counts only when the step that picks it is held by the applicant (so it will be
     * skipped) or the flow has no step that picks it; otherwise it is dropped and that step's holder picks it on forward.
     * Otherwise any applicant could pre-route the request to a department of their choice.
     */
    private Long applicantTarget(LoadedFlow flow, long requesterId, Long given) {
        List<WorkflowStep> pickers = flow.steps().values().stream()
            .filter(s -> Boolean.TRUE.equals(s.getPicksTargetDepartment())).toList();
        boolean requesterPicks = pickers.stream()
            .anyMatch(s -> resolver.resolve(s, requesterId, null).filter(id -> id == requesterId).isPresent());
        if (given == null) {
            // the picking step is the requester's own and is skipped: default to their own department
            return requesterPicks ? resolver.departmentOf(requesterId).orElse(null) : null;
        }
        return pickers.isEmpty() || requesterPicks ? given : null;
    }

    @Override
    @Transactional
    public WorkflowStatusResponse act(RequestType type, long requestId, WorkflowActionRequest action) {
        Live live = scope.open(type, requestId, scope.related(type, requestId));
        requireStep(live, action.stepKey());
        authorization.assertMayAct(live.step(), live.ctx().requesterId(), live.ctx().targetDepartmentId());
        String comment = WorkflowInputs.text(action.comment());
        if (action.outcome() == StepOutcome.REJECT && comment == null) {
            throw new BusinessRuleException("COMMENT_REQUIRED", "Give a reason for the rejection.");
        }
        Ctx ctx = checkOutcome(live, action.outcome(), action.targetDepartmentId());
        return transition(live, ctx, action.outcome(), ACTION_OF.get(action.outcome()), comment, null);
    }

    @Override
    @Transactional
    public WorkflowStatusResponse override(RequestType type, long requestId, WorkflowOverrideRequest action) {
        Live live = scope.open(type, requestId, scope.related(type, requestId));
        requireStep(live, action.stepKey());
        authorization.assertMayOverride(live.step(), live.ctx().requesterId(), live.ctx().targetDepartmentId());
        String reason = WorkflowInputs.text(action.reason());
        if (reason == null) {
            throw new BusinessRuleException("REASON_REQUIRED", "Give the reason for the override.");
        }
        Ctx ctx = checkOutcome(live, action.outcome(), action.targetDepartmentId());
        return transition(live, ctx, action.outcome(), HistoryAction.OVERRIDDEN, reason, action.outcome().name());
    }

    /** Requester only (identity from the security context); anyone else gets 403 before the state is revealed. */
    @Override
    @Transactional
    public WorkflowStatusResponse cancel(RequestType type, long requestId) {
        Optional<Live> related = scope.related(type, requestId);
        Long caller = accessGuard.currentEmployeeId();
        if (caller == null || caller != ports.access(type).requesterEmployeeId(requestId)) {
            throw new AccessDeniedException("Only the requester can cancel this request.");
        }
        Live live = scope.open(type, requestId, related);
        engine.cancel(live.ctx().processInstanceId());
        history.byCaller(type, requestId, live.task().key(), HistoryAction.CANCELLED, null, null);
        audit(type, requestId, HistoryAction.CANCELLED, live.task().key(), null);
        finish(live.ctx(), CANCELLED, null);
        return closed(type, requestId, CANCELLED);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkflowStatusResponse status(RequestType type, long requestId) {
        Optional<Live> live = scope.related(type, requestId);
        if (live.isPresent()) {
            Live l = live.get();
            return waiting(type, requestId, l.step(), l.ctx().flow().outcomes(l.step()),
                authorization.mayAct(l.step(), l.ctx().requesterId(), l.ctx().targetDepartmentId()),
                authorization.mayOverride(l.step(), l.ctx().requesterId(), l.ctx().targetDepartmentId()));
        }
        String stage = ports.access(type).stage(requestId).orElseThrow(() ->
            new BusinessRuleException("NOT_IN_WORKFLOW", "This request does not use the approval workflow."));
        return closed(type, requestId, stage);
    }

    /**
     * Active departments for the department picker, only for the live holder of a step that picks it. Everyone
     * else (requester, past actor, overseer who does not hold the step, unrelated, closed or legacy request, a step
     * that does not pick) gets the same 403, so nothing is revealed.
     */
    @Override
    @Transactional(readOnly = true)
    public List<DepartmentLookupResponse> targetDepartments(RequestType type, long requestId) {
        Live live = scope.related(type, requestId).orElseThrow(() -> new AccessDeniedException(RequestScope.NO_ACCESS));
        boolean picks = Boolean.TRUE.equals(live.step().getPicksTargetDepartment())
            && live.ctx().flow().outcomes(live.step()).contains(StepOutcome.FORWARD);
        if (!picks || !authorization.mayAct(live.step(), live.ctx().requesterId(), live.ctx().targetDepartmentId())) {
            throw new AccessDeniedException(RequestScope.NO_ACCESS);
        }
        return departments.getActiveLookup();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RequestHistoryResponse> history(RequestType type, long requestId) {
        scope.related(type, requestId); // the participant check of spec 10: the HTTP layer needs no other
        return history.list(type, requestId);
    }

    /** Completes the live task (the serialisation point), records it, then enters whatever comes next. */
    private WorkflowStatusResponse transition(Live live, Ctx ctx, StepOutcome outcome, HistoryAction action,
                                              String text, String reasonCode) {
        engine.complete(live.task().id(), outcome);
        history.byCaller(ctx.type(), ctx.requestId(), live.task().key(), action, text, reasonCode);
        if (outcome != StepOutcome.FORWARD || action == HistoryAction.OVERRIDDEN) {
            audit(ctx.type(), ctx.requestId(), action, live.task().key(), outcome);
        }
        boolean targetChanged = picksDepartment(live.step(), outcome);
        StepEntry.Entry next = stepEntry.enter(ctx);
        if (next instanceof StepEntry.Rest rest) {
            return land(ctx, rest, targetChanged);
        }
        String stage = ((StepEntry.Ended) next).stage();
        finish(ctx, stage, outcome == StepOutcome.REJECT ? text : null);
        return closed(ctx.type(), ctx.requestId(), stage);
    }

    /** Writes the waiting step to the request: markEntered when the department was (re)chosen, else markStage. */
    private WorkflowStatusResponse land(Ctx ctx, StepEntry.Rest rest, boolean entered) {
        WorkflowStep step = rest.step();
        String stage = PENDING_PREFIX + step.getStepKey();
        StoredApprover approver = StoredApprover.of(step, rest.approverEmployeeId(), ctx.requesterId(),
            resolver.roleHolding(step, ctx.requesterId()).orElse(null));
        String approverType = step.getApproverType().name();
        WorkflowRequestAccess access = ports.access(ctx.type());
        if (entered) {
            access.markEntered(ctx.requestId(), ctx.flow().definitionId(), ctx.processInstanceId(),
                ctx.targetDepartmentId(), stage, step.getStepKey(), approverType, approver.employeeId(), approver.role());
        } else {
            access.markStage(ctx.requestId(), stage, step.getStepKey(), approverType, approver.employeeId(),
                approver.role());
        }
        return waiting(ctx.type(), ctx.requestId(), step, ctx.flow().outcomes(step),
            authorization.mayAct(step, ctx.requesterId(), ctx.targetDepartmentId()),
            authorization.mayOverride(step, ctx.requesterId(), ctx.targetDepartmentId()));
    }

    /**
     * The step is shown to every related caller, but outcomes (and the department picker) only to who may act;
     * the override flag only to who may override, and whether an override must pick the department to everyone.
     */
    private WorkflowStatusResponse waiting(RequestType type, long requestId, WorkflowStep step,
                                           Set<StepOutcome> allowed, boolean callerMayAct,
                                           boolean callerMayOverride) {
        Set<StepOutcome> offered = callerMayAct ? allowed : Set.of();
        WorkflowRequestAccess.Brief brief = ports.access(type).brief(requestId);
        return new WorkflowStatusResponse(type, requestId, PENDING_PREFIX + step.getStepKey(), step.getStepKey(),
            step.getLabelEn(), step.getLabelAr(), List.copyOf(offered),
            Boolean.TRUE.equals(step.getPicksTargetDepartment()) && offered.contains(StepOutcome.FORWARD),
            brief.requesterEmployeeId(), brief.requesterName(), brief.summary(), callerMayOverride,
            Boolean.TRUE.equals(step.getPicksTargetDepartment()) && allowed.contains(StepOutcome.FORWARD));
    }

    /** Downstream effects first, then the request's terminal stage: a handler failure leaves the stage untouched. */
    private void finish(Ctx ctx, String stage, String reason) {
        var handler = ports.handler(ctx.type());
        switch (stage) {
            case WorkflowValidator.APPROVED -> handler.onApproved(ctx.requestId());
            case WorkflowValidator.REJECTED -> handler.onRejected(ctx.requestId(), reason);
            case CANCELLED -> handler.onCancelled(ctx.requestId());
            default -> throw new IllegalStateException("Unknown terminal stage " + stage);
        }
        ports.access(ctx.type()).markTerminal(ctx.requestId(), stage);
    }

    /**
     * After the participant check and before step authorization, so a double click or a second approver (both
     * related to the request) gets 409 rather than 403; an unrelated caller never gets this far.
     */
    private static void requireStep(Live live, String stepKey) {
        if (!live.task().key().equals(stepKey)) {
            throw FlowEngine.alreadyHandled();
        }
    }

    /** The outcome must be offered by the step; a step that picks the department needs a valid one to forward. */
    private Ctx checkOutcome(Live live, StepOutcome outcome, Long targetDepartmentId) {
        if (!live.ctx().flow().outcomes(live.step()).contains(outcome)) {
            throw new BusinessRuleException("OUTCOME_NOT_ALLOWED", "This step does not offer " + outcome + ".");
        }
        if (!picksDepartment(live.step(), outcome)) {
            return live.ctx();
        }
        if (targetDepartmentId == null) {
            throw new BusinessRuleException("TARGET_DEPARTMENT_REQUIRED", "Choose the department for this request.");
        }
        inputs.requireActiveDepartment(targetDepartmentId);
        return live.ctx().withTarget(targetDepartmentId);
    }

    /** This transition chooses the request's department (the branch step forwarding). */
    private static boolean picksDepartment(WorkflowStep step, StepOutcome outcome) {
        return Boolean.TRUE.equals(step.getPicksTargetDepartment()) && outcome == StepOutcome.FORWARD;
    }

    /** Ids and codes only, never the comment text. */
    private void audit(RequestType type, long requestId, HistoryAction action, String stepKey, StepOutcome outcome) {
        String detail = "step " + stepKey + (outcome == null ? "" : " outcome " + outcome);
        audit.record("WORKFLOW_" + action.name(), type.name(), requestId, detail);
    }

    private WorkflowStatusResponse closed(RequestType type, long requestId, String stage) {
        WorkflowRequestAccess.Brief brief = ports.access(type).brief(requestId);
        return new WorkflowStatusResponse(type, requestId, stage, null, null, null, List.of(), false,
            brief.requesterEmployeeId(), brief.requesterName(), brief.summary(), false, false);
    }
}
