package com.hrms.workflow.service.impl;

import com.hrms.workflow.entity.WorkflowRoute;
import com.hrms.workflow.entity.WorkflowStep;
import com.hrms.workflow.enums.RequestType;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns validated steps and routes into a BPMN model (spec 5.4) through Flowable's model API, never string-built XML.
 * One user task per step (id = step key); an exclusive gateway only where a step has more than one route, choosing on
 * process variable {@code outcome} plus allow-listed numeric facts. Conditions are assembled only from enum names,
 * allow-listed field names and operators and a checked number. Labels are never put into the model: Flowable
 * evaluates a task's name and description as expressions, so a label like "${x}" would run.
 * Anything outside the allow-lists throws: the validator has already run, so that is a programming error.
 * Numeric process facts used in conditions (e.g. totalDays) must be passed as java.math.BigDecimal, so a half day
 * (0.5) compares exactly against the literal.
 */
@Component
public class BpmnGenerator {

    public static final String OUTCOME_VARIABLE = "outcome";
    public static final String END_PREFIX = "end_";

    /** NUMBER(14,3): up to 11 integer digits and 3 decimals, no exponent. */
    private static final Pattern NUMBER = Pattern.compile("-?\\d{1,11}(\\.\\d{1,3})?");
    private static final Map<String, String> EL_OPERATORS = Map.of(
        ">", ">", ">=", ">=", "<", "<", "<=", "<=", "=", "==", "<>", "!=");

    public static String processKey(RequestType type) {
        return "wf_" + type.name();
    }

    public BpmnModel generate(RequestType type, List<WorkflowStep> steps, List<WorkflowRoute> routes) {
        Process process = new Process();
        process.setId(processKey(type));
        process.setExecutable(true);
        StartEvent start = new StartEvent();
        start.setId("start");
        process.addFlowElement(start);
        List<WorkflowStep> ordered = steps.stream().sorted(Comparator.comparing(WorkflowStep::getPositionNo)).toList();
        for (WorkflowStep s : ordered) {
            process.addFlowElement(userTask(s.getStepKey()));
        }
        flow(process, start, node(process, ordered.get(0).getStepKey()), null);
        for (WorkflowStep s : ordered) {
            List<WorkflowRoute> own = routes.stream().filter(r -> s.getStepKey().equals(r.getStep().getStepKey()))
                .sorted(Comparator.comparing(WorkflowRoute::getPositionNo)).toList();
            connect(process, type, s, own);
        }
        BpmnModel model = new BpmnModel();
        model.addProcess(process);
        return model;
    }

    private UserTask userTask(String key) {
        if (key == null || !WorkflowValidator.STEP_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Step key is not a plain identifier");
        }
        UserTask task = new UserTask();
        task.setId(key);
        task.setName(key);
        return task;
    }

    /** One unconditional route flows straight on; anything else goes through a gateway, conditions in route order. */
    private void connect(Process process, RequestType type, WorkflowStep step, List<WorkflowRoute> own) {
        FlowNode task = node(process, step.getStepKey());
        if (own.size() == 1 && !isConditional(own.get(0))) {
            flow(process, task, target(process, own.get(0).getTarget()), null);
            return;
        }
        ExclusiveGateway gateway = new ExclusiveGateway();
        gateway.setId("gw_" + step.getStepKey());
        process.addFlowElement(gateway);
        flow(process, task, gateway, null);
        for (WorkflowRoute r : own) {
            flow(process, gateway, target(process, r.getTarget()), condition(type, r));
        }
    }

    private String condition(RequestType type, WorkflowRoute r) {
        String outcome = OUTCOME_VARIABLE + " == '" + r.getOutcome().name() + "'";
        if (!isConditional(r)) {
            return "${" + outcome + "}";
        }
        String field = r.getCondField();
        String operator = r.getCondOperator() == null ? null : EL_OPERATORS.get(r.getCondOperator());
        String value = r.getCondValue() == null ? "" : r.getCondValue().trim();
        if (!WorkflowValidator.CONDITION_FIELDS.getOrDefault(type, Set.of()).contains(field)
            || operator == null || !NUMBER.matcher(value).matches()) {
            throw new IllegalArgumentException("Condition outside the allow-lists");
        }
        String number = new BigDecimal(value).stripTrailingZeros().toPlainString();
        return "${" + outcome + " && " + field + " " + operator + " " + number + "}";
    }

    private boolean isConditional(WorkflowRoute r) {
        return r.getCondField() != null || r.getCondOperator() != null || r.getCondValue() != null;
    }

    /** A step key, or APPROVED / REJECTED as end events end_APPROVED / end_REJECTED (created on first use). */
    private FlowNode target(Process process, String target) {
        if (WorkflowValidator.APPROVED.equals(target) || WorkflowValidator.REJECTED.equals(target)) {
            String id = END_PREFIX + target;
            if (process.getFlowElement(id) == null) {
                EndEvent end = new EndEvent();
                end.setId(id);
                process.addFlowElement(end);
            }
            return node(process, id);
        }
        return node(process, target);
    }

    private FlowNode node(Process process, String id) {
        if (id == null || !(process.getFlowElement(id) instanceof FlowNode n)) {
            throw new IllegalArgumentException("Route target is not a step of this flow");
        }
        return n;
    }

    private void flow(Process process, FlowNode from, FlowNode to, String condition) {
        SequenceFlow f = new SequenceFlow(from.getId(), to.getId());
        f.setId("flow_" + (process.getFlowElements().size() + 1));
        f.setConditionExpression(condition);
        from.getOutgoingFlows().add(f);
        to.getIncomingFlows().add(f);
        process.addFlowElement(f);
    }
}
