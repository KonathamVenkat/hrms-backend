package com.hrms.workflow.service.impl;

import com.hrms.workflow.enums.StepOutcome;
import lombok.RequiredArgsConstructor;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * The workflow's only door into Flowable. Completing a task is the serialisation point for concurrent actions: a
 * task someone else already completed (or is completing) becomes HTTP 409, never a second completion.
 */
@Component
@RequiredArgsConstructor
class FlowEngine {

    static final String CANCEL_REASON = WorkflowServiceImpl.CANCELLED;

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;

    /** The open user task of an instance: its id, its key (= step key) and the process definition it runs on. */
    record OpenTask(String id, String key, String processDefinitionId) {
    }

    /** 409: the step was handled meanwhile (double click, second approver, stale tab). */
    static OptimisticLockingFailureException alreadyHandled() {
        return new OptimisticLockingFailureException("STEP_ALREADY_HANDLED");
    }

    String start(String processDefinitionId, String businessKey, Map<String, Object> variables) {
        return runtimeService.startProcessInstanceById(processDefinitionId, businessKey, variables).getId();
    }

    Optional<OpenTask> openTask(String processInstanceId) {
        Task t = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();
        return t == null ? Optional.empty()
            : Optional.of(new OpenTask(t.getId(), t.getTaskDefinitionKey(), t.getProcessDefinitionId()));
    }

    void complete(String taskId, StepOutcome outcome) {
        try {
            taskService.complete(taskId, Map.of(BpmnGenerator.OUTCOME_VARIABLE, outcome.name()));
        } catch (FlowableObjectNotFoundException | FlowableOptimisticLockingException e) {
            throw alreadyHandled();
        }
    }

    /** APPROVED / REJECTED from the end event the finished instance reached (end_APPROVED / end_REJECTED). */
    String terminalStage(String processInstanceId) {
        HistoricProcessInstance done = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(processInstanceId).singleResult();
        String end = done == null ? null : done.getEndActivityId();
        if (end == null || !end.startsWith(BpmnGenerator.END_PREFIX)) {
            throw new IllegalStateException("Process instance " + processInstanceId + " has no known end event");
        }
        return end.substring(BpmnGenerator.END_PREFIX.length());
    }

    void cancel(String processInstanceId) {
        try {
            runtimeService.deleteProcessInstance(processInstanceId, CANCEL_REASON);
        } catch (FlowableObjectNotFoundException | FlowableOptimisticLockingException e) {
            throw alreadyHandled();
        }
    }
}
