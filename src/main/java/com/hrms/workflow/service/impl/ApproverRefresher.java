package com.hrms.workflow.service.impl;

import com.hrms.employee.service.OrganisationChanged;
import com.hrms.workflow.entity.WorkflowStep;
import com.hrms.workflow.enums.ApproverType;
import com.hrms.workflow.repository.WorkflowStepRepository;
import com.hrms.workflow.service.WorkflowRequestAccess;
import com.hrms.workflow.service.WorkflowRequestAccess.OpenRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keeps the stored current approver of waiting requests in step with the organisation: when a head, secretary or
 * branch manager is replaced, or an employee changes branch, every request waiting at a person step is re-resolved,
 * so the new holder finds it in the inbox and the old one no longer does. Authorization never depended on the stored
 * value; this only fixes who is shown the item.
 * <p>
 * Runs once per committing transaction, however many OrganisationChanged events it published, and only after the
 * commit (so it sees the change). Each re-pointed row is written in its own REQUIRES_NEW transaction: a row an
 * approver moved meanwhile is skipped (that action already stored the right approver) and a version conflict is
 * logged and skipped, so the HR change that triggered this can never fail because of it. The requests are read in
 * chunks by stage (IDX_LR_STAGE for leave), limited to the stages of person steps. Not {@code
 * TransactionalEventListener}: that runs once per event, this must run once per transaction.
 */
@Slf4j
@Component
class ApproverRefresher {

    static final int CHUNK = 200;

    private final WorkflowPorts ports;
    private final WorkflowStepRepository steps;
    private final ApproverResolver resolver;
    private final TransactionTemplate reader;
    private final TransactionTemplate writer;

    ApproverRefresher(WorkflowPorts ports, WorkflowStepRepository steps, ApproverResolver resolver,
                      PlatformTransactionManager transactions) {
        this.ports = ports;
        this.steps = steps;
        this.resolver = resolver;
        this.reader = new TransactionTemplate(transactions);
        this.reader.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.reader.setReadOnly(true);
        this.writer = new TransactionTemplate(transactions);
        this.writer.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Queues one refresh after the publishing transaction commits; outside a transaction it runs at once. */
    @EventListener
    void onOrganisationChanged(OrganisationChanged event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            refreshSafely();
            return;
        }
        boolean queued = TransactionSynchronizationManager.getSynchronizations().stream()
            .anyMatch(AfterCommit.class::isInstance);
        if (!queued) {
            TransactionSynchronizationManager.registerSynchronization(new AfterCommit());
        }
    }

    private final class AfterCommit implements TransactionSynchronization {
        @Override
        public void afterCommit() {
            refreshSafely();
        }
    }

    /** The organisation change is already committed: a failure here is logged, never thrown at its caller. */
    private void refreshSafely() {
        try {
            refresh();
        } catch (RuntimeException e) {
            log.error("Refreshing the stored approvers of waiting workflow requests failed", e);
        }
    }

    void refresh() {
        Set<String> stages = reader.execute(s -> steps.findPersonStepKeys().stream()
            .map(key -> WorkflowServiceImpl.PENDING_PREFIX + key).collect(Collectors.toSet()));
        if (stages == null || stages.isEmpty()) {
            return;
        }
        Map<Long, Map<String, WorkflowStep>> flows = new HashMap<>();
        Map<String, Optional<Long>> resolved = new HashMap<>();
        for (WorkflowRequestAccess access : ports.allAccess()) {
            long after = 0;
            int read;
            do {
                long from = after;
                List<Change> changes = new ArrayList<>();
                List<OpenRequest> chunk = reader.execute(s -> {
                    List<OpenRequest> rows = access.openRequests(stages, from, CHUNK);
                    rows.forEach(r -> changeOf(r, flows, resolved).ifPresent(changes::add));
                    return rows;
                });
                read = chunk == null ? 0 : chunk.size();
                if (read > 0) {
                    after = chunk.get(read - 1).requestId();
                }
                changes.forEach(c -> write(access, c));
            } while (read == CHUNK);
        }
    }

    /** The approver the request should store now, when it differs from the stored one. */
    private Optional<Change> changeOf(OpenRequest r, Map<Long, Map<String, WorkflowStep>> flows,
                                      Map<String, Optional<Long>> resolved) {
        if (r.definitionId() == null) {
            return Optional.empty();
        }
        WorkflowStep step = flows.computeIfAbsent(r.definitionId(), this::stepsOf).get(r.stepKey());
        if (step == null) {
            return Optional.empty();
        }
        // One lookup per branch-manager requester or per department, not per request.
        String key = step.getApproverType() + ":" + (step.getApproverType() == ApproverType.BRANCH_MANAGER
            ? r.requesterEmployeeId() : r.targetDepartmentId());
        Long holder = resolved.computeIfAbsent(key,
            k -> resolver.resolve(step, r.requesterEmployeeId(), r.targetDepartmentId())).orElse(null);
        StoredApprover now = StoredApprover.of(step, holder, r.requesterEmployeeId());
        boolean same = Objects.equals(now.employeeId(), r.approverEmployeeId())
            && Objects.equals(now.role(), r.approverRole());
        return same ? Optional.empty()
            : Optional.of(new Change(r, step.getApproverType().name(), now));
    }

    private record Change(OpenRequest request, String approverType, StoredApprover approver) {
    }

    private void write(WorkflowRequestAccess access, Change c) {
        OpenRequest r = c.request();
        try {
            writer.executeWithoutResult(s -> {
                if (!access.stage(r.requestId()).filter(r.stage()::equals).isPresent()) {
                    return; // moved on since it was read: the action that moved it stored the right approver
                }
                access.markStage(r.requestId(), r.stage(), r.stepKey(), c.approverType(),
                    c.approver().employeeId(), c.approver().role());
            });
        } catch (OptimisticLockingFailureException e) {
            log.warn("Stored approver of {} request {} not refreshed: it changed concurrently", access.type(),
                r.requestId());
        } catch (RuntimeException e) {
            log.error("Stored approver of {} request {} not refreshed: {}", access.type(), r.requestId(),
                e.getClass().getSimpleName());
        }
    }

    private Map<String, WorkflowStep> stepsOf(Long definitionId) {
        Map<String, WorkflowStep> byKey = new HashMap<>();
        steps.findByDefinitionIdOrderByPositionNo(definitionId).forEach(s -> byKey.put(s.getStepKey(), s));
        return byKey;
    }
}
