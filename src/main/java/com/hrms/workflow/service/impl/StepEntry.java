package com.hrms.workflow.service.impl;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.workflow.entity.WorkflowStep;
import com.hrms.workflow.enums.ApproverType;
import com.hrms.workflow.enums.RequestType;
import com.hrms.workflow.enums.StepOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Entering the next step after a transition (spec 7.3 as ruled for v1): a person step the requester holds is skipped,
 * a secretary step with nobody is skipped, a branch or head step with nobody waits for an HR_ADMIN override.
 * ROLE steps are never skipped (the requester simply cannot act on them). Every skip and wait writes AUTO_ROUTED.
 * The loop is bounded by the number of steps: the validator forbids loops, so more turns is a bug.
 */
@Component
@RequiredArgsConstructor
class StepEntry {

    static final String NO_APPROVER = "NO_APPROVER";
    static final String SKIP_REQUESTER = "SKIP_REQUESTER_IS_APPROVER";
    static final String SKIP_NO_APPROVER = "SKIP_NO_APPROVER";
    static final String SKIP_WOULD_APPROVE = "SKIP_WOULD_APPROVE";

    private final FlowEngine engine;
    private final ApproverResolver resolver;
    private final HistoryWriter history;

    /** The request being moved: who raised it, the department it is routed to, its instance and its flow version. */
    record Ctx(RequestType type, long requestId, long requesterId, Long targetDepartmentId,
               String processInstanceId, LoadedFlow flow) {
        Ctx withTarget(Long target) {
            return new Ctx(type, requestId, requesterId, target, processInstanceId, flow);
        }
    }

    sealed interface Entry permits Rest, Ended {
    }

    /** Waiting at {@code step}; {@code approverEmployeeId} is the resolved person (null for ROLE or nobody). */
    record Rest(FlowEngine.OpenTask task, WorkflowStep step, Long approverEmployeeId) implements Entry {
    }

    /** The flow ended: APPROVED or REJECTED. */
    record Ended(String stage) implements Entry {
    }

    Entry enter(Ctx ctx) {
        for (int turn = 0; turn <= ctx.flow().size(); turn++) {
            Optional<FlowEngine.OpenTask> open = engine.openTask(ctx.processInstanceId());
            if (open.isEmpty()) {
                return new Ended(engine.terminalStage(ctx.processInstanceId()));
            }
            Rest rest = restOrSkip(ctx, open.get());
            if (rest != null) {
                return rest;
            }
        }
        throw new IllegalStateException("Workflow did not settle on a step for " + ctx.type() + " " + ctx.requestId());
    }

    /** The step to wait at, or null after completing it as a skip. */
    private Rest restOrSkip(Ctx ctx, FlowEngine.OpenTask task) {
        WorkflowStep step = ctx.flow().step(task.key());
        if (step.getApproverType() == ApproverType.ROLE) {
            return new Rest(task, step, null);
        }
        Optional<Long> approver = resolver.resolve(step, ctx.requesterId(), ctx.targetDepartmentId());
        boolean requesterHolds = approver.filter(id -> id == ctx.requesterId()).isPresent();
        boolean nobodyNeeded = approver.isEmpty() && step.getApproverType() == ApproverType.DEPARTMENT_SECRETARY;
        if (!requesterHolds && !nobodyNeeded) {
            if (approver.isEmpty()) {
                history.bySystem(ctx.type(), ctx.requestId(), step.getStepKey(), NO_APPROVER);
            }
            return new Rest(task, step, approver.orElse(null));
        }
        if (Boolean.TRUE.equals(step.getPicksTargetDepartment()) && ctx.targetDepartmentId() == null) {
            throw new BusinessRuleException("TARGET_DEPARTMENT_REQUIRED", "Choose the department for this request.");
        }
        StepOutcome outcome = skipOutcome(ctx.flow().outcomes(step));
        if (outcome == null || ctx.flow().mayApprove(step, outcome)) {
            // A custom flow whose skipped step would approve outright waits instead (never self-approval): an
            // HR_ADMIN may override it (resolved person = requester), or the requester cancels.
            history.bySystem(ctx.type(), ctx.requestId(), step.getStepKey(), SKIP_WOULD_APPROVE);
            return new Rest(task, step, approver.orElse(null));
        }
        engine.complete(task.id(), outcome);
        history.bySystem(ctx.type(), ctx.requestId(), step.getStepKey(), requesterHolds ? SKIP_REQUESTER : SKIP_NO_APPROVER);
        return null;
    }

    /** A skip moves the request on: forward when the step can, otherwise approve. */
    private static StepOutcome skipOutcome(Set<StepOutcome> offered) {
        if (offered.contains(StepOutcome.FORWARD)) {
            return StepOutcome.FORWARD;
        }
        return offered.contains(StepOutcome.APPROVE) ? StepOutcome.APPROVE : null;
    }
}
