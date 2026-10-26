package com.hrms.workflow;

import com.hrms.employee.DatabaseTest;
import com.hrms.employee.EmployeeApplication;
import com.hrms.workflow.entity.*;
import com.hrms.workflow.enums.*;
import com.hrms.workflow.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The five workflow entities map V10 exactly: save through the repositories, flush, clear, reload, compare. */
@SpringBootTest(classes = EmployeeApplication.class)
@Transactional
class WorkflowEntityMappingDbTest extends DatabaseTest {

    @Autowired WorkflowDefinitionRepository definitions;
    @Autowired WorkflowStepRepository steps;
    @Autowired WorkflowRouteRepository routes;
    @Autowired WorkflowSettingRepository settings;
    @Autowired RequestHistoryRepository histories;
    @Autowired EntityManager em;

    @Test
    void definitionWithStepsRoutesHistoryAndSettingRoundTrip() {
        WorkflowDefinition def = new WorkflowDefinition();
        def.setRequestType(RequestType.OVERTIME);
        def.setVersionNo(1);
        Long defId = definitions.saveAndFlush(def).getDefinitionId();

        WorkflowStep head = steps.saveAndFlush(step(defId, "HEAD", 1, ApproverType.DEPARTMENT_HEAD, null, true));
        WorkflowStep hr = steps.saveAndFlush(step(defId, "HR", 2, ApproverType.ROLE, "HR_ADMIN", false));
        routes.saveAndFlush(route(head, StepOutcome.APPROVE, "HR", "totalDays", ">", "10.5"));
        routes.saveAndFlush(route(hr, StepOutcome.APPROVE, "APPROVED", null, null, null));

        RequestHistory h = new RequestHistory();
        h.setRequestType(RequestType.OVERTIME);
        h.setRequestId(77L);
        h.setStepKey("HEAD");
        h.setAction(HistoryAction.AUTO_ROUTED);
        h.setComment("skipped");
        Long historyId = histories.save(h).getHistoryId();
        em.flush();

        WorkflowSetting setting = new WorkflowSetting();
        setting.setSettingKey("WORKFLOW_ENABLED_OVERTIME");
        setting.setSettingValue("true");
        settings.saveAndFlush(setting);
        em.clear();

        WorkflowDefinition d = definitions.findById(defId).orElseThrow();
        assertEquals(RequestType.OVERTIME, d.getRequestType());
        assertEquals(WorkflowDefinition.STATUS_DRAFT, d.getStatus());
        assertNotNull(d.getVersion());
        assertNotNull(d.getCreatedAt());
        assertEquals(d.getDefinitionId(),
            definitions.findByRequestTypeAndStatus(RequestType.OVERTIME, "DRAFT").orElseThrow().getDefinitionId());

        List<WorkflowStep> loaded = steps.findByDefinitionIdOrderByPositionNo(defId);
        assertEquals(List.of("HEAD", "HR"), loaded.stream().map(WorkflowStep::getStepKey).toList());
        assertTrue(loaded.get(0).getPicksTargetDepartment());
        assertFalse(loaded.get(1).getPicksTargetDepartment());
        assertEquals("HR_ADMIN", loaded.get(1).getApproverRole());
        assertEquals("نص", loaded.get(0).getLabelAr());

        List<WorkflowRoute> loadedRoutes = routes.findByStepDefinitionId(defId);
        assertEquals(2, loadedRoutes.size());
        WorkflowRoute conditional = loadedRoutes.stream().filter(r -> r.getCondField() != null).findFirst().orElseThrow();
        assertEquals(">", conditional.getCondOperator());
        assertEquals(0, new java.math.BigDecimal("10.5").compareTo(new java.math.BigDecimal(conditional.getCondValue())));
        assertEquals("HR", conditional.getTarget());

        RequestHistory rh = em.find(RequestHistory.class, historyId);
        assertEquals(HistoryAction.AUTO_ROUTED, rh.getAction());
        assertEquals("skipped", rh.getComment());
        assertNotNull(rh.getActedAt());
        assertEquals(1, histories.findByRequestTypeAndRequestIdOrderByActedAtAscHistoryIdAsc(RequestType.OVERTIME, 77L).size());

        assertEquals("true", settings.findById("WORKFLOW_ENABLED_OVERTIME").orElseThrow().getSettingValue());
        assertEquals("false", settings.findById("WORKFLOW_ENABLED_LEAVE").orElseThrow().getSettingValue());
    }

    private WorkflowStep step(Long defId, String key, int pos, ApproverType type, String role, boolean picks) {
        WorkflowStep s = new WorkflowStep();
        s.setDefinitionId(defId);
        s.setStepKey(key);
        s.setPositionNo(pos);
        s.setLabelEn(key);
        s.setLabelAr("نص");
        s.setApproverType(type);
        s.setApproverRole(role);
        s.setPicksTargetDepartment(picks);
        return s;
    }

    private WorkflowRoute route(WorkflowStep step, StepOutcome outcome, String target, String field, String op, String value) {
        WorkflowRoute r = new WorkflowRoute();
        r.setStep(step);
        r.setOutcome(outcome);
        r.setTarget(target);
        r.setCondField(field);
        r.setCondOperator(op);
        r.setCondValue(value);
        return r;
    }
}
