package com.hrms.workflow.service.impl;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.workflow.entity.WorkflowRoute;
import com.hrms.workflow.entity.WorkflowStep;
import com.hrms.workflow.enums.ApproverType;
import com.hrms.workflow.enums.RequestType;
import com.hrms.workflow.enums.StepOutcome;

import java.util.ArrayList;
import java.util.List;

/**
 * The v1 flow each request type gets before an admin edits it (spec section 17): branch manager forwards to the
 * department secretary, who forwards to the head; head, General Manager and HR approve or reject. Unsaved entities.
 */
final class DefaultWorkflowFactory {

    record Flow(List<WorkflowStep> steps, List<WorkflowRoute> routes) {
    }

    private DefaultWorkflowFactory() {
    }

    static Flow forType(RequestType type) {
        if (type != RequestType.LEAVE) {
            throw new BusinessRuleException("WORKFLOW_NO_DEFAULT", "No default workflow exists for " + type + ".");
        }
        WorkflowStep branch = step("BRANCH", 1, "Branch manager", "مدير الفرع", ApproverType.BRANCH_MANAGER, null);
        branch.setPicksTargetDepartment(true);
        WorkflowStep secretary = step("SECRETARY", 2, "Department secretary", "سكرتير القسم",
            ApproverType.DEPARTMENT_SECRETARY, null);
        WorkflowStep head = step("HEAD", 3, "Department head", "رئيس القسم", ApproverType.DEPARTMENT_HEAD, null);
        WorkflowStep gm = step("GM", 4, "General manager", "المدير العام", ApproverType.ROLE, "GENERAL_MANAGER");
        WorkflowStep hr = step("HR", 5, "HR processing", "معالجة الموارد البشرية", ApproverType.ROLE, "HR_ADMIN");

        List<WorkflowRoute> routes = new ArrayList<>();
        routes.add(route(branch, StepOutcome.FORWARD, "SECRETARY", 1));
        routes.add(route(branch, StepOutcome.REJECT, WorkflowValidator.REJECTED, 2));
        routes.add(route(secretary, StepOutcome.FORWARD, "HEAD", 1));
        routes.add(route(head, StepOutcome.APPROVE, "GM", 1));
        routes.add(route(head, StepOutcome.REJECT, WorkflowValidator.REJECTED, 2));
        routes.add(route(gm, StepOutcome.APPROVE, "HR", 1));
        routes.add(route(gm, StepOutcome.REJECT, WorkflowValidator.REJECTED, 2));
        routes.add(route(hr, StepOutcome.APPROVE, WorkflowValidator.APPROVED, 1));
        routes.add(route(hr, StepOutcome.REJECT, WorkflowValidator.REJECTED, 2));
        return new Flow(List.of(branch, secretary, head, gm, hr), routes);
    }

    static WorkflowStep step(String key, int position, String en, String ar, ApproverType type, String role) {
        WorkflowStep s = new WorkflowStep();
        s.setStepKey(key);
        s.setPositionNo(position);
        s.setLabelEn(en);
        s.setLabelAr(ar);
        s.setApproverType(type);
        s.setApproverRole(role);
        return s;
    }

    static WorkflowRoute route(WorkflowStep from, StepOutcome outcome, String target, int position) {
        WorkflowRoute r = new WorkflowRoute();
        r.setStep(from);
        r.setOutcome(outcome);
        r.setTarget(target);
        r.setPositionNo(position);
        return r;
    }
}
