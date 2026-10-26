package com.hrms.workflow.service.impl;

import com.hrms.workflow.entity.WorkflowRoute;
import com.hrms.workflow.entity.WorkflowStep;
import com.hrms.workflow.enums.StepOutcome;
import com.hrms.workflow.repository.WorkflowRouteRepository;
import com.hrms.workflow.repository.WorkflowStepRepository;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** One published version's steps and routes, loaded once per action (two queries). Step labels live only here. */
record LoadedFlow(long definitionId, Map<String, WorkflowStep> steps, Map<Long, List<WorkflowRoute>> routesByStep) {

    static LoadedFlow load(long definitionId, WorkflowStepRepository steps, WorkflowRouteRepository routes) {
        Map<String, WorkflowStep> byKey = new LinkedHashMap<>();
        steps.findByDefinitionIdOrderByPositionNo(definitionId).forEach(s -> byKey.put(s.getStepKey(), s));
        // getStep().getStepId() reads the proxy's id: no extra query per route
        Map<Long, List<WorkflowRoute>> byStep = routes.findByStepDefinitionId(definitionId).stream()
            .collect(Collectors.groupingBy(r -> r.getStep().getStepId()));
        return new LoadedFlow(definitionId, byKey, byStep);
    }

    WorkflowStep step(String key) {
        WorkflowStep s = steps.get(key);
        if (s == null) {
            throw new IllegalStateException("Flowable task " + key + " is not a step of definition " + definitionId);
        }
        return s;
    }

    int size() {
        return steps.size();
    }

    /** In enum order (FORWARD, APPROVE, REJECT). */
    Set<StepOutcome> outcomes(WorkflowStep step) {
        Set<StepOutcome> out = EnumSet.noneOf(StepOutcome.class);
        routesByStep.getOrDefault(step.getStepId(), List.of()).forEach(r -> out.add(r.getOutcome()));
        return out;
    }

    /** Whether some route of this outcome ends the flow approved (so skipping it would approve without a decision). */
    boolean mayApprove(WorkflowStep step, StepOutcome outcome) {
        return routesByStep.getOrDefault(step.getStepId(), List.of()).stream()
            .anyMatch(r -> r.getOutcome() == outcome && WorkflowValidator.APPROVED.equals(r.getTarget()));
    }
}
