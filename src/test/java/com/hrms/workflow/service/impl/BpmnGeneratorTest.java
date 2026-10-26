package com.hrms.workflow.service.impl;

import com.hrms.workflow.entity.WorkflowRoute;
import com.hrms.workflow.entity.WorkflowStep;
import com.hrms.workflow.enums.ApproverType;
import com.hrms.workflow.enums.RequestType;
import com.hrms.workflow.enums.StepOutcome;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.UserTask;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class BpmnGeneratorTest {

    /** The only shape a generated condition may take: allow-listed outcome, field, operator and a plain number. */
    private static final Pattern SAFE_CONDITION = Pattern.compile(
        "\\$\\{outcome == '(FORWARD|APPROVE|REJECT)'( && totalDays (>|>=|<|<=|==|!=) -?\\d+(\\.\\d+)?)?}");

    private final BpmnGenerator generator = new BpmnGenerator();

    @Test
    void defaultLeaveFlowIsValidAndHasOneUserTaskPerStep() {
        DefaultWorkflowFactory.Flow v1 = DefaultWorkflowFactory.forType(RequestType.LEAVE);
        assertEquals(List.of(), new WorkflowValidator().validate(v1.steps(), v1.routes(), RequestType.LEAVE));

        Process p = generator.generate(RequestType.LEAVE, v1.steps(), v1.routes()).getMainProcess();
        assertEquals("wf_LEAVE", p.getId());
        assertEquals(List.of("BRANCH", "SECRETARY", "HEAD", "GM", "HR"),
            p.findFlowElementsOfType(UserTask.class).stream().map(UserTask::getId).toList());
        assertEquals(List.of("end_APPROVED", "end_REJECTED"),
            p.findFlowElementsOfType(EndEvent.class).stream().map(EndEvent::getId).sorted().toList());
    }

    @Test
    void gatewayOnlyWhereAStepHasMoreThanOneRoute() {
        DefaultWorkflowFactory.Flow v1 = DefaultWorkflowFactory.forType(RequestType.LEAVE);
        Process p = generator.generate(RequestType.LEAVE, v1.steps(), v1.routes()).getMainProcess();

        assertEquals(List.of("gw_BRANCH", "gw_GM", "gw_HEAD", "gw_HR"),
            p.findFlowElementsOfType(ExclusiveGateway.class).stream().map(ExclusiveGateway::getId).sorted().toList());
        List<SequenceFlow> secretaryOut = ((FlowNode) p.getFlowElement("SECRETARY")).getOutgoingFlows();
        assertEquals(1, secretaryOut.size());
        assertEquals("HEAD", secretaryOut.get(0).getTargetRef());
        assertNull(secretaryOut.get(0).getConditionExpression());

        List<SequenceFlow> hrOut = ((FlowNode) p.getFlowElement("gw_HR")).getOutgoingFlows();
        assertEquals(List.of("end_APPROVED", "end_REJECTED"), hrOut.stream().map(SequenceFlow::getTargetRef).toList());
        assertEquals("${outcome == 'APPROVE'}", hrOut.get(0).getConditionExpression());
        for (SequenceFlow f : p.findFlowElementsOfType(SequenceFlow.class)) {
            if (f.getConditionExpression() != null) {
                assertTrue(SAFE_CONDITION.matcher(f.getConditionExpression()).matches(), f.getConditionExpression());
            }
        }
    }

    @Test
    void conditionalRoutesComeFirstThenTheOtherwiseRoute() {
        List<SequenceFlow> out = gatewayFlows(conditionalFlow(">", "10"), "gw_HEAD");
        assertEquals("${outcome == 'APPROVE' && totalDays > 10}", out.get(0).getConditionExpression());
        assertEquals("GM", out.get(0).getTargetRef());
        assertEquals("${outcome == 'APPROVE'}", out.get(1).getConditionExpression());
        assertEquals("HR", out.get(1).getTargetRef());
    }

    @Test
    void operatorsAreTranslatedFromTheAllowList() {
        assertEquals("${outcome == 'APPROVE' && totalDays == 2.5}",
            gatewayFlows(conditionalFlow("=", " 2.50 "), "gw_HEAD").get(0).getConditionExpression());
        assertEquals("${outcome == 'APPROVE' && totalDays <= 0.5}",
            gatewayFlows(conditionalFlow("<=", "0.5"), "gw_HEAD").get(0).getConditionExpression());
        assertEquals("${outcome == 'APPROVE' && totalDays > 10}",
            gatewayFlows(conditionalFlow(">", "10.000"), "gw_HEAD").get(0).getConditionExpression());
        assertEquals("${outcome == 'APPROVE' && totalDays != 3}",
            gatewayFlows(conditionalFlow("<>", "3"), "gw_HEAD").get(0).getConditionExpression());
    }

    @Test
    void labelsNeverReachTheBpmnBecauseFlowableEvaluatesTaskNamesAsExpressions() {
        DefaultWorkflowFactory.Flow v1 = DefaultWorkflowFactory.forType(RequestType.LEAVE);
        v1.steps().forEach(s -> {
            s.setLabelEn("${x}\"><script>alert(1)</script>");
            s.setLabelAr("#{y}");
        });
        BpmnModel model = generator.generate(RequestType.LEAVE, v1.steps(), v1.routes());
        String xml = new String(new BpmnXMLConverter().convertToXML(model), StandardCharsets.UTF_8);

        assertFalse(xml.contains("${x}"), xml);
        assertFalse(xml.contains("#{y}"), xml);
        assertFalse(xml.contains("script"), xml);
        model.getMainProcess().findFlowElementsOfType(UserTask.class)
            .forEach(t -> assertEquals(t.getId(), t.getName()));
    }

    @Test
    void anythingOutsideTheAllowListsIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> generate(conditionalFlow(">", "10} ${evil")));
        assertThrows(IllegalArgumentException.class, () -> generate(conditionalFlow(">", "1E20")));
        assertThrows(IllegalArgumentException.class, () -> generate(conditionalFlow("=~", "10")));

        DefaultWorkflowFactory.Flow badField = conditionalFlow(">", "10");
        badField.routes().get(0).setCondField("x}${evil");
        assertThrows(IllegalArgumentException.class, () -> generate(badField));

        DefaultWorkflowFactory.Flow otherType = conditionalFlow(">", "10");
        assertThrows(IllegalArgumentException.class,
            () -> generator.generate(RequestType.OVERTIME, otherType.steps(), otherType.routes()));

        DefaultWorkflowFactory.Flow badKey = DefaultWorkflowFactory.forType(RequestType.LEAVE);
        badKey.steps().get(0).setStepKey("BRANCH\"/><x");
        assertThrows(IllegalArgumentException.class, () -> generate(badKey));

        DefaultWorkflowFactory.Flow noPosition = DefaultWorkflowFactory.forType(RequestType.LEAVE);
        noPosition.routes().get(0).setPositionNo(null);
        assertThrows(IllegalArgumentException.class, () -> generate(noPosition));
    }

    private void generate(DefaultWorkflowFactory.Flow flow) {
        generator.generate(RequestType.LEAVE, flow.steps(), flow.routes());
    }

    private List<SequenceFlow> gatewayFlows(DefaultWorkflowFactory.Flow flow, String gatewayId) {
        Process p = generator.generate(RequestType.LEAVE, flow.steps(), flow.routes()).getMainProcess();
        return ((FlowNode) p.getFlowElement(gatewayId)).getOutgoingFlows();
    }

    /** HEAD: APPROVE when totalDays OP value -> GM, otherwise -> HR; REJECT -> REJECTED. GM and HR decide. */
    static DefaultWorkflowFactory.Flow conditionalFlow(String operator, String value) {
        WorkflowStep head = step("HEAD", 1, ApproverType.DEPARTMENT_HEAD, null);
        WorkflowStep gm = step("GM", 2, ApproverType.ROLE, "GENERAL_MANAGER");
        WorkflowStep hr = step("HR", 3, ApproverType.ROLE, "HR_ADMIN");
        WorkflowRoute when = route(head, StepOutcome.APPROVE, "GM", 1);
        when.setCondField("totalDays");
        when.setCondOperator(operator);
        when.setCondValue(value);
        return new DefaultWorkflowFactory.Flow(List.of(head, gm, hr), List.of(
            when,
            route(head, StepOutcome.APPROVE, "HR", 2),
            route(head, StepOutcome.REJECT, "REJECTED", 3),
            route(gm, StepOutcome.APPROVE, "APPROVED", 1),
            route(gm, StepOutcome.REJECT, "REJECTED", 2),
            route(hr, StepOutcome.APPROVE, "APPROVED", 1),
            route(hr, StepOutcome.REJECT, "REJECTED", 2)));
    }

    private static WorkflowStep step(String key, int position, ApproverType type, String role) {
        return DefaultWorkflowFactory.step(key, position, key, key, type, role);
    }

    private static WorkflowRoute route(WorkflowStep from, StepOutcome outcome, String target, int position) {
        return DefaultWorkflowFactory.route(from, outcome, target, position);
    }
}
