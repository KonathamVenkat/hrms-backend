package com.hrms.workflow.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.audit.CurrentAuditor;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.workflow.entity.WorkflowDefinition;
import com.hrms.workflow.entity.WorkflowRoute;
import com.hrms.workflow.entity.WorkflowStep;
import com.hrms.workflow.enums.RequestType;
import com.hrms.workflow.repository.WorkflowDefinitionRepository;
import com.hrms.workflow.repository.WorkflowRouteRepository;
import com.hrms.workflow.repository.WorkflowStepRepository;
import com.hrms.workflow.service.WorkflowDefinitionService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WorkflowDefinitionServiceImpl implements WorkflowDefinitionService {

    private final WorkflowDefinitionRepository definitions;
    private final WorkflowStepRepository steps;
    private final WorkflowRouteRepository routes;
    private final WorkflowValidator validator;
    private final BpmnGenerator generator;
    private final RepositoryService repositoryService;
    private final AuditTrail audit;

    @Override
    @Transactional
    public WorkflowDefinition publishDefault(RequestType type) {
        DefaultWorkflowFactory.Flow flow = DefaultWorkflowFactory.forType(type);
        return publish(type, flow.steps(), flow.routes());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkflowDefinition> active(RequestType type) {
        return definitions.findByRequestTypeAndStatus(type, WorkflowDefinition.STATUS_ACTIVE);
    }

    /**
     * Validate first (nothing is deployed or saved otherwise), deploy, then save the new ACTIVE version with its steps
     * and routes after retiring the old one. Not annotated: it always runs in the caller's transaction, which is
     * publishDefault's (a self-call would skip a proxy annotation anyway); the later admin publish must be
     * {@code @Transactional} the same way, and the tests call it inside a transaction. Package-private: the admin
     * publish and the tests pass their own steps and routes (unsaved entities, routes pointing at those steps).
     */
    WorkflowDefinition publish(RequestType type, List<WorkflowStep> flowSteps, List<WorkflowRoute> flowRoutes) {
        List<String> errors = validator.validate(flowSteps, flowRoutes, type);
        if (!errors.isEmpty()) {
            throw new BusinessRuleException("WORKFLOW_INVALID",
                "The workflow cannot be published: " + String.join(", ", errors));
        }
        String key = BpmnGenerator.processKey(type);
        Deployment deployment = repositoryService.createDeployment().name(key)
            .addBpmnModel(key + ".bpmn20.xml", generator.generate(type, flowSteps, flowRoutes)).deploy();
        String processDefinitionId = repositoryService.createProcessDefinitionQuery()
            .deploymentId(deployment.getId()).singleResult().getId();

        int versionNo = definitions.findTopByRequestTypeOrderByVersionNoDesc(type)
            .map(d -> d.getVersionNo() + 1).orElse(1);
        retireActive(type);
        WorkflowDefinition def = new WorkflowDefinition();
        def.setRequestType(type);
        def.setVersionNo(versionNo);
        def.setStatus(WorkflowDefinition.STATUS_ACTIVE);
        def.setFlowableDeploymentId(deployment.getId());
        def.setFlowableProcessDefinitionId(processDefinitionId);
        def.setPublishedBy(CurrentAuditor.name());
        def.setPublishedAt(LocalDateTime.now());
        definitions.save(def);
        flowSteps.forEach(s -> s.setDefinitionId(def.getDefinitionId()));
        steps.saveAll(flowSteps);
        routes.saveAll(flowRoutes);
        audit.record("WORKFLOW_PUBLISHED", "WORKFLOW_DEFINITION", def.getDefinitionId(), type + " version " + versionNo);
        return def;
    }

    /** Flushed at once: Hibernate runs inserts before updates, and the unique index allows one ACTIVE row per type. */
    private void retireActive(RequestType type) {
        definitions.findByRequestTypeAndStatus(type, WorkflowDefinition.STATUS_ACTIVE).ifPresent(old -> {
            old.setStatus(WorkflowDefinition.STATUS_RETIRED);
            definitions.saveAndFlush(old);
        });
    }
}
