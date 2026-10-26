package com.hrms.workflow.service.impl;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.employee.DatabaseTest;
import com.hrms.employee.EmployeeApplication;
import com.hrms.workflow.entity.WorkflowDefinition;
import com.hrms.workflow.entity.WorkflowRoute;
import com.hrms.workflow.enums.RequestType;
import com.hrms.workflow.enums.StepOutcome;
import com.hrms.workflow.repository.WorkflowDefinitionRepository;
import com.hrms.workflow.repository.WorkflowRouteRepository;
import com.hrms.workflow.repository.WorkflowStepRepository;
import com.hrms.workflow.service.WorkflowDefinitionService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.Deployment;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Publishing deploys a generated BPMN, keeps exactly one ACTIVE version, and running requests keep their version. */
@SpringBootTest(classes = EmployeeApplication.class)
@Transactional
class WorkflowPublishDbTest extends DatabaseTest {

    @Autowired WorkflowDefinitionService service;
    @Autowired WorkflowDefinitionServiceImpl impl;
    @Autowired WorkflowDefinitionRepository definitions;
    @Autowired WorkflowStepRepository steps;
    @Autowired WorkflowRouteRepository routes;
    @Autowired RepositoryService repositoryService;
    @Autowired RuntimeService runtimeService;
    @Autowired TaskService taskService;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;

    @Test
    void firstPublishMakesV1ActiveWithItsStepsRoutesAndAudit() {
        WorkflowDefinition v1 = service.publishDefault(RequestType.LEAVE);

        assertEquals(1, v1.getVersionNo());
        assertEquals(WorkflowDefinition.STATUS_ACTIVE, v1.getStatus());
        assertNotNull(v1.getPublishedBy());
        assertNotNull(v1.getPublishedAt());
        assertEquals(v1.getFlowableProcessDefinitionId(), repositoryService.createProcessDefinitionQuery()
            .deploymentId(v1.getFlowableDeploymentId()).processDefinitionKey("wf_LEAVE").singleResult().getId());
        assertEquals(v1.getDefinitionId(), service.active(RequestType.LEAVE).orElseThrow().getDefinitionId());
        assertEquals(List.of("BRANCH", "SECRETARY", "HEAD", "GM", "HR"), steps
            .findByDefinitionIdOrderByPositionNo(v1.getDefinitionId()).stream().map(s -> s.getStepKey()).toList());
        assertEquals(9, routes.findByStepDefinitionId(v1.getDefinitionId()).size());
        assertEquals(1, jdbc.queryForObject("select count(*) from AUDIT_EVENTS where ACTION = 'WORKFLOW_PUBLISHED'"
            + " and TARGET_ID = ?", Long.class, String.valueOf(v1.getDefinitionId())));
        assertTrue(service.active(RequestType.OVERTIME).isEmpty());
    }

    @Test
    void secondPublishCreatesV2RetiresV1AndLeavesOneActive() {
        WorkflowDefinition v1 = service.publishDefault(RequestType.LEAVE);
        WorkflowDefinition v2 = service.publishDefault(RequestType.LEAVE);

        assertEquals(2, v2.getVersionNo());
        assertNotEquals(v1.getFlowableProcessDefinitionId(), v2.getFlowableProcessDefinitionId());
        assertEquals(WorkflowDefinition.STATUS_RETIRED, definitions.findById(v1.getDefinitionId()).orElseThrow().getStatus());
        assertEquals(1, jdbc.queryForObject("select count(*) from WORKFLOW_DEFINITIONS"
            + " where REQUEST_TYPE = 'LEAVE' and STATUS = 'ACTIVE'", Long.class));
        assertEquals(v2.getDefinitionId(), service.active(RequestType.LEAVE).orElseThrow().getDefinitionId());
    }

    @Test
    void aRequestStartedOnV1FinishesOnV1AfterADifferentV2IsPublished() {
        WorkflowDefinition v1 = service.publishDefault(RequestType.LEAVE);
        String onV1 = runtimeService.startProcessInstanceByKey("wf_LEAVE").getId();
        assertEquals("SECRETARY", complete(onV1, StepOutcome.FORWARD));
        assertEquals("HEAD", complete(onV1, StepOutcome.FORWARD));

        DefaultWorkflowFactory.Flow changed = withoutGm();
        WorkflowDefinition v2 = impl.publish(RequestType.LEAVE, changed.steps(), changed.routes());
        assertEquals(2, v2.getVersionNo());

        assertEquals("GM", complete(onV1, StepOutcome.APPROVE)); // v1: HEAD approves to GM
        assertEquals(v1.getFlowableProcessDefinitionId(), currentTask(onV1).getProcessDefinitionId());
        String onV2 = runtimeService.startProcessInstanceByKey("wf_LEAVE").getId();
        assertEquals(v2.getFlowableProcessDefinitionId(), currentTask(onV2).getProcessDefinitionId());
        complete(onV2, StepOutcome.FORWARD);
        complete(onV2, StepOutcome.FORWARD);
        assertEquals("HR", complete(onV2, StepOutcome.APPROVE)); // v2: HEAD approves straight to HR
    }

    /** The default flow with the GM step removed and HEAD's approval going to HR. */
    private DefaultWorkflowFactory.Flow withoutGm() {
        DefaultWorkflowFactory.Flow f = DefaultWorkflowFactory.forType(RequestType.LEAVE);
        List<WorkflowRoute> r = new ArrayList<>(f.routes());
        r.removeIf(x -> "GM".equals(x.getStep().getStepKey()));
        r.stream().filter(x -> "HEAD".equals(x.getStep().getStepKey()) && x.getOutcome() == StepOutcome.APPROVE)
            .forEach(x -> x.setTarget("HR"));
        return new DefaultWorkflowFactory.Flow(f.steps().stream().filter(x -> !"GM".equals(x.getStepKey())).toList(), r);
    }

    private Task currentTask(String instanceId) {
        return taskService.createTaskQuery().processInstanceId(instanceId).singleResult();
    }

    /** Completes the instance's open task with the outcome and returns the next task's key. */
    private String complete(String instanceId, StepOutcome outcome) {
        taskService.complete(currentTask(instanceId).getId(), Map.of("outcome", outcome.name()));
        return currentTask(instanceId).getTaskDefinitionKey();
    }

    @Test
    void aBadStepKeyIsWorkflowInvalidAndDeploysAndSavesNothing() {
        DefaultWorkflowFactory.Flow bad = DefaultWorkflowFactory.forType(RequestType.LEAVE);
        bad.steps().get(0).setStepKey("${x}");
        long deployments = repositoryService.createDeploymentQuery().count();
        long rows = definitions.count();

        BusinessRuleException e = assertThrows(BusinessRuleException.class,
            () -> impl.publish(RequestType.LEAVE, bad.steps(), bad.routes()));

        assertEquals("WORKFLOW_INVALID", e.getRuleCode());
        assertTrue(e.getMessage().contains("BAD_STEP_KEY"));
        assertEquals(deployments, repositoryService.createDeploymentQuery().count());
        assertEquals(rows, definitions.count());
    }

    /**
     * Not under the test transaction: a step insert that fails (label longer than LABEL_EN, which the validator does
     * not check) must also undo the Flowable deployment made earlier in the same transaction.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aFailureAfterDeployingRollsBackTheDeploymentToo() {
        Set<String> deploymentsBefore = deploymentIds();
        long maxDefinitionBefore = jdbc.queryForObject(
            "select coalesce(max(DEFINITION_ID), 0) from WORKFLOW_DEFINITIONS", Long.class);
        DefaultWorkflowFactory.Flow flow = DefaultWorkflowFactory.forType(RequestType.LEAVE);
        flow.steps().get(0).setLabelEn("x".repeat(101));
        try {
            RuntimeException failure = assertThrows(RuntimeException.class,
                () -> tx.executeWithoutResult(s -> impl.publish(RequestType.LEAVE, flow.steps(), flow.routes())));
            // The step insert runs after the deployment in publish(), so the deployment existed when it failed.
            assertTrue(failure.getMessage().contains("LABEL_EN"), failure.getMessage());
            assertEquals(deploymentsBefore, deploymentIds());
            assertEquals(maxDefinitionBefore, jdbc.queryForObject(
                "select coalesce(max(DEFINITION_ID), 0) from WORKFLOW_DEFINITIONS", Long.class));
        } finally {
            removeLeftovers(deploymentsBefore, maxDefinitionBefore);
        }
    }

    private Set<String> deploymentIds() {
        return repositoryService.createDeploymentQuery().list().stream().map(Deployment::getId).collect(Collectors.toSet());
    }

    /** Only acts if the rollback did not happen, so a failing run still leaves the shared database as it was. */
    private void removeLeftovers(Set<String> deploymentsBefore, long maxDefinitionBefore) {
        deploymentIds().stream().filter(id -> !deploymentsBefore.contains(id))
            .forEach(id -> repositoryService.deleteDeployment(id, true));
        tx.executeWithoutResult(s -> {
            String newDefs = "select DEFINITION_ID from WORKFLOW_DEFINITIONS where DEFINITION_ID > ?";
            jdbc.update("delete from WORKFLOW_ROUTES where STEP_ID in (select STEP_ID from WORKFLOW_STEPS"
                + " where DEFINITION_ID in (" + newDefs + "))", maxDefinitionBefore);
            jdbc.update("delete from WORKFLOW_STEPS where DEFINITION_ID in (" + newDefs + ")", maxDefinitionBefore);
            jdbc.update("delete from AUDIT_EVENTS where ACTION = 'WORKFLOW_PUBLISHED' and TARGET_ID in ("
                + "select to_char(DEFINITION_ID) from WORKFLOW_DEFINITIONS where DEFINITION_ID > ?)", maxDefinitionBefore);
            jdbc.update("delete from WORKFLOW_DEFINITIONS where DEFINITION_ID > ?", maxDefinitionBefore);
        });
    }

    @Test
    void anInvalidDefinitionDeploysAndSavesNothing() {
        DefaultWorkflowFactory.Flow bad = DefaultWorkflowFactory.forType(RequestType.LEAVE);
        List<WorkflowRoute> noReject = new ArrayList<>(bad.routes());
        noReject.removeIf(r -> "HR".equals(r.getStep().getStepKey()) && r.getOutcome() == StepOutcome.REJECT);
        noReject.get(0).setCondValue("not a number"); // would blow up at flush if validation ran late
        long deployments = repositoryService.createDeploymentQuery().count();
        long rows = definitions.count();

        BusinessRuleException e = assertThrows(BusinessRuleException.class,
            () -> impl.publish(RequestType.LEAVE, bad.steps(), noReject));

        assertEquals("WORKFLOW_INVALID", e.getRuleCode());
        assertEquals(deployments, repositoryService.createDeploymentQuery().count());
        assertEquals(rows, definitions.count());
    }

    @Test
    void aRequestTimeConditionPicksTheNextStep() {
        DefaultWorkflowFactory.Flow flow = BpmnGeneratorTest.conditionalFlow(">", "10");
        assertEquals(List.of(), new WorkflowValidator().validate(flow.steps(), flow.routes(), RequestType.LEAVE));
        String deploymentId = repositoryService.createDeployment()
            .addBpmnModel("wf_LEAVE.bpmn20.xml", new BpmnGenerator().generate(RequestType.LEAVE, flow.steps(), flow.routes()))
            .deploy().getId();
        String definitionId = repositoryService.createProcessDefinitionQuery().deploymentId(deploymentId).singleResult().getId();

        assertEquals("HR", nextStepAfterHeadApproves(definitionId, 3));
        assertEquals("GM", nextStepAfterHeadApproves(definitionId, 15));
        assertEquals("HR", nextStepAfterHeadApproves(definitionId, 10));
    }

    private String nextStepAfterHeadApproves(String definitionId, int totalDays) {
        String instanceId = runtimeService.startProcessInstanceById(definitionId, Map.of("totalDays", BigDecimal.valueOf(totalDays))).getId();
        Task head = taskService.createTaskQuery().processInstanceId(instanceId).singleResult();
        assertEquals("HEAD", head.getTaskDefinitionKey());
        taskService.complete(head.getId(), Map.of("outcome", StepOutcome.APPROVE.name()));
        return taskService.createTaskQuery().processInstanceId(instanceId).singleResult().getTaskDefinitionKey();
    }
}
