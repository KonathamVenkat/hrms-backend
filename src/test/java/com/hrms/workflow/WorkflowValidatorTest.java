package com.hrms.workflow;

import com.hrms.workflow.entity.WorkflowRoute;
import com.hrms.workflow.entity.WorkflowStep;
import com.hrms.workflow.enums.ApproverType;
import com.hrms.workflow.enums.RequestType;
import com.hrms.workflow.enums.StepOutcome;
import com.hrms.workflow.service.impl.WorkflowValidator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.hrms.workflow.enums.StepOutcome.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plain unit test: the publish-time rules of spec 5.4 on in-memory steps and routes. */
class WorkflowValidatorTest {

    private final WorkflowValidator validator = new WorkflowValidator();
    private final List<WorkflowStep> steps = new ArrayList<>();
    private final List<WorkflowRoute> routes = new ArrayList<>();

    private WorkflowStep step(String key, ApproverType type) {
        WorkflowStep s = new WorkflowStep();
        s.setStepKey(key);
        s.setPositionNo(steps.size() + 1);
        s.setApproverType(type);
        steps.add(s);
        return s;
    }

    private void route(WorkflowStep from, StepOutcome outcome, String target) {
        route(from, outcome, target, null, null, null);
    }

    private void route(WorkflowStep from, StepOutcome outcome, String target, String field, String op, String value) {
        WorkflowRoute r = new WorkflowRoute();
        r.setStep(from);
        r.setOutcome(outcome);
        r.setTarget(target);
        r.setCondField(field);
        r.setCondOperator(op);
        r.setCondValue(value);
        r.setPositionNo((int) routes.stream().filter(x -> x.getStep() == from).count() + 1);
        routes.add(r);
    }

    private List<String> validate() {
        return validator.validate(steps, routes, RequestType.LEAVE);
    }

    private void defaultV1() {
        WorkflowStep branch = step("BRANCH", ApproverType.BRANCH_MANAGER);
        WorkflowStep secretary = step("SECRETARY", ApproverType.DEPARTMENT_SECRETARY);
        WorkflowStep head = step("HEAD", ApproverType.DEPARTMENT_HEAD);
        WorkflowStep gm = step("GM", ApproverType.ROLE);
        WorkflowStep hr = step("HR", ApproverType.ROLE);
        route(branch, FORWARD, "SECRETARY");
        route(branch, REJECT, "REJECTED");
        route(secretary, FORWARD, "HEAD");
        route(head, APPROVE, "GM");
        route(head, REJECT, "REJECTED");
        route(gm, APPROVE, "HR");
        route(gm, REJECT, "REJECTED");
        route(hr, APPROVE, "APPROVED");
        route(hr, REJECT, "REJECTED");
    }

    /** One approver that approves or rejects; conditions are added by each test. */
    private WorkflowStep single() {
        WorkflowStep hr = step("HR", ApproverType.ROLE);
        route(hr, REJECT, "REJECTED");
        return hr;
    }

    @Test
    void defaultV1IsPublishable() {
        defaultV1();
        assertEquals(List.of(), validate());
    }

    @Test
    void stepWithoutAnyRouteHasNoRouteForOutcome() {
        defaultV1();
        routes.removeIf(r -> r.getStep().getStepKey().equals("HR"));
        assertTrue(validate().contains("NO_ROUTE_FOR_OUTCOME"));
    }

    @Test
    void routeToUnknownTargetHasNoRouteForOutcome() {
        WorkflowStep hr = single();
        route(hr, APPROVE, "NOWHERE");
        assertTrue(validate().contains("NO_ROUTE_FOR_OUTCOME"));
    }

    @Test
    void unreachableStepIsReported() {
        defaultV1();
        WorkflowStep orphan = step("ORPHAN", ApproverType.ROLE);
        route(orphan, APPROVE, "APPROVED");
        route(orphan, REJECT, "REJECTED");
        assertEquals(List.of("UNREACHABLE_STEP:ORPHAN"), validate());
    }

    @Test
    void cycleIsReported() {
        WorkflowStep a = step("A", ApproverType.ROLE);
        WorkflowStep b = step("B", ApproverType.ROLE);
        route(a, APPROVE, "B");
        route(b, APPROVE, "A");
        route(b, REJECT, "REJECTED");
        route(a, REJECT, "APPROVED");
        assertTrue(validate().contains("LOOP"));
    }

    @Test
    void conditionalRouteWithoutOtherwiseIsReported() {
        WorkflowStep hr = single();
        route(hr, APPROVE, "APPROVED", "totalDays", ">", "10");
        assertTrue(validate().contains("MISSING_OTHERWISE_ROUTE"));
    }

    @Test
    void overlappingConditionsAreReported() {
        WorkflowStep hr = single();
        route(hr, APPROVE, "APPROVED", "totalDays", ">", "5");
        route(hr, APPROVE, "APPROVED", "totalDays", ">", "3");
        route(hr, APPROVE, "APPROVED");
        assertEquals(List.of("OVERLAPPING_CONDITIONS"), validate());
    }

    @Test
    void greaterAndLessOrEqualOnTheSameValueIsValid() {
        WorkflowStep hr = single();
        route(hr, APPROVE, "APPROVED", "totalDays", ">", "10");
        route(hr, APPROVE, "APPROVED", "totalDays", "<=", "10");
        route(hr, APPROVE, "APPROVED");
        assertEquals(List.of(), validate());
    }

    @Test
    void notEqualOverlapsAnythingButItsOwnValue() {
        WorkflowStep hr = single();
        route(hr, APPROVE, "APPROVED", "totalDays", "<>", "10");
        route(hr, APPROVE, "APPROVED", "totalDays", "=", "10");
        route(hr, APPROVE, "APPROVED");
        assertEquals(List.of(), validate());
    }

    @Test
    void flowWithoutAnApprovingRouteIsReported() {
        WorkflowStep hr = step("HR", ApproverType.ROLE);
        route(hr, REJECT, "REJECTED");
        assertTrue(validate().contains("NO_APPROVING_STEP"));
    }

    @Test
    void unknownConditionFieldIsReported() {
        WorkflowStep hr = single();
        route(hr, APPROVE, "APPROVED", "hackerField", ">", "1");
        route(hr, APPROVE, "APPROVED");
        assertTrue(validate().contains("UNKNOWN_CONDITION_FIELD"));
    }

    @Test
    void nonNumericOrUnsupportedConditionIsReported() {
        for (String[] bad : new String[][] {{">", "abc"}, {">", "NaN"}, {">", "Infinity"}, {"!=", "1"}, {">", null}}) {
            routes.clear();
            steps.clear();
            WorkflowStep hr = single();
            route(hr, APPROVE, "APPROVED", "totalDays", bad[0], bad[1]);
            route(hr, APPROVE, "APPROVED");
            assertTrue(validate().contains("BAD_CONDITION_VALUE"), bad[0] + " " + bad[1]);
        }
    }

    @Test
    void secretaryOfferingApproveOrRejectIsReported() {
        for (StepOutcome outcome : new StepOutcome[] {APPROVE, REJECT}) {
            routes.clear();
            steps.clear();
            defaultV1();
            route(steps.get(1), outcome, outcome == APPROVE ? "APPROVED" : "REJECTED");
            assertTrue(validate().contains("SECRETARY_CANNOT_DECIDE"), outcome.name());
        }
    }

    @Test
    void nullOperatorOrNullFieldOrNullValueIsBadConditionAndNeverThrows() {
        String[][] partial = {{"totalDays", null, "5"}, {null, ">", "5"}, {"totalDays", ">", null}};
        for (String[] p : partial) {
            routes.clear();
            steps.clear();
            WorkflowStep hr = single();
            route(hr, APPROVE, "APPROVED", p[0], p[1], p[2]);
            route(hr, APPROVE, "APPROVED");
            assertEquals(List.of("BAD_CONDITION_VALUE"), validate(), String.join(",", String.valueOf(p[0]), String.valueOf(p[1]), String.valueOf(p[2])));
        }
    }

    @Test
    void unknownFieldWithGoodOperatorAndValueIsOnlyUnknownField() {
        WorkflowStep hr = single();
        route(hr, APPROVE, "APPROVED", "hackerField", ">", "1");
        route(hr, APPROVE, "APPROVED");
        assertEquals(List.of("UNKNOWN_CONDITION_FIELD"), validate());
    }

    @Test
    void valueThatDoesNotFitTheColumnIsBad() {
        for (String value : new String[] {"1E20", "1.23456", "123456789012", "-123456789012.5"}) {
            routes.clear();
            steps.clear();
            WorkflowStep hr = single();
            route(hr, APPROVE, "APPROVED", "totalDays", ">", value);
            route(hr, APPROVE, "APPROVED");
            assertEquals(List.of("BAD_CONDITION_VALUE"), validate(), value);
        }
    }

    @Test
    void valueAtTheColumnLimitIsAccepted() {
        WorkflowStep hr = single();
        route(hr, APPROVE, "APPROVED", "totalDays", ">", "99999999999.999");
        route(hr, APPROVE, "APPROVED");
        assertEquals(List.of(), validate());
    }

    @Test
    void headWithApproveButNoRejectIsMissingAnOutcome() {
        defaultV1();
        routes.removeIf(r -> r.getStep().getStepKey().equals("HEAD") && r.getOutcome() == REJECT);
        assertEquals(List.of("NO_ROUTE_FOR_OUTCOME"), validate());
    }

    @Test
    void hrStepWithoutRejectIsFlagged() {
        defaultV1();
        routes.removeIf(r -> r.getStep().getStepKey().equals("HR") && r.getOutcome() == REJECT);
        assertEquals(List.of("NO_ROUTE_FOR_OUTCOME"), validate());
    }

    @Test
    void secretaryWithoutForwardIsFlagged() {
        defaultV1();
        routes.removeIf(r -> r.getStep().getStepKey().equals("SECRETARY"));
        route(steps.get(1), REJECT, "REJECTED");
        assertTrue(validate().containsAll(List.of("NO_ROUTE_FOR_OUTCOME", "SECRETARY_CANNOT_DECIDE")));
    }

    @Test
    void equalAndLessOrEqualOnTheSameValueOverlap() {
        WorkflowStep hr = single();
        route(hr, APPROVE, "APPROVED", "totalDays", "=", "10");
        route(hr, APPROVE, "APPROVED", "totalDays", "<=", "10");
        route(hr, APPROVE, "APPROVED");
        assertEquals(List.of("OVERLAPPING_CONDITIONS"), validate());
    }

    @Test
    void greaterOrEqualAndLessOrEqualOnTheSameValueOverlap() {
        WorkflowStep hr = single();
        route(hr, APPROVE, "APPROVED", "totalDays", ">=", "10");
        route(hr, APPROVE, "APPROVED", "totalDays", "<=", "10");
        route(hr, APPROVE, "APPROVED");
        assertEquals(List.of("OVERLAPPING_CONDITIONS"), validate());
    }

    @Test
    void otherwiseRouteThatIsNotLastIsMissingOtherwise() {
        WorkflowStep hr = single();
        route(hr, APPROVE, "APPROVED");
        route(hr, APPROVE, "APPROVED", "totalDays", ">", "10");
        assertEquals(List.of("MISSING_OTHERWISE_ROUTE"), validate());
    }

    @Test
    void twoUnconditionalRoutesForOneOutcomeOverlap() {
        WorkflowStep hr = single();
        route(hr, APPROVE, "APPROVED");
        route(hr, APPROVE, "APPROVED");
        assertEquals(List.of("OVERLAPPING_CONDITIONS"), validate());
    }

    @Test
    void longLeaveGoesToGmAndShortLeaveToHrIsClean() {
        WorkflowStep head = step("HEAD", ApproverType.DEPARTMENT_HEAD);
        WorkflowStep gm = step("GM", ApproverType.ROLE);
        WorkflowStep hr = step("HR", ApproverType.ROLE);
        route(head, REJECT, "REJECTED");
        route(head, APPROVE, "GM", "totalDays", ">", "10");
        route(head, APPROVE, "HR", "totalDays", "<=", "10");
        route(head, APPROVE, "HR");
        route(gm, APPROVE, "HR");
        route(gm, REJECT, "REJECTED");
        route(hr, APPROVE, "APPROVED");
        route(hr, REJECT, "REJECTED");
        assertEquals(List.of(), validate());
    }

    @Test
    void approvingRouteOnlyOnAnUnreachableStepIsNoApprovingStep() {
        WorkflowStep head = step("HEAD", ApproverType.DEPARTMENT_HEAD);
        WorkflowStep orphan = step("ORPHAN", ApproverType.ROLE);
        route(head, FORWARD, "REJECTED");
        route(head, REJECT, "REJECTED");
        route(orphan, APPROVE, "APPROVED");
        route(orphan, REJECT, "REJECTED");
        assertEquals(List.of("UNREACHABLE_STEP:ORPHAN", "NO_APPROVING_STEP"), validate());
    }

    @Test
    void duplicateStepKeyIsReported() {
        defaultV1();
        WorkflowStep dup = step("HEAD", ApproverType.ROLE);
        route(dup, APPROVE, "APPROVED");
        route(dup, REJECT, "REJECTED");
        assertTrue(validate().contains("UNREACHABLE_STEP:HEAD"));
    }

    @Test
    void routeOfAStepOutsideTheFlowIsReported() {
        defaultV1();
        WorkflowStep stranger = new WorkflowStep();
        stranger.setStepKey("STRANGER");
        route(stranger, APPROVE, "APPROVED");
        assertEquals(List.of("UNREACHABLE_STEP:STRANGER"), validate());
    }

    @Test
    void stepKeysMustBePlainUpperCaseIdentifiers() {
        for (String key : List.of("head", "${x}", "1HEAD", "H".repeat(31), "HE AD", "HEAD\"/>", "")) {
            steps.clear();
            routes.clear();
            WorkflowStep bad = step(key, ApproverType.DEPARTMENT_HEAD);
            route(bad, APPROVE, "APPROVED");
            route(bad, REJECT, "REJECTED");
            assertEquals(List.of("BAD_STEP_KEY:" + key), validate(), key);
        }
    }

    @Test
    void thirtyCharacterKeyIsAccepted() {
        WorkflowStep ok = step("H".repeat(30), ApproverType.DEPARTMENT_HEAD);
        route(ok, APPROVE, "APPROVED");
        route(ok, REJECT, "REJECTED");
        assertEquals(List.of(), validate());
    }

    @Test
    void terminalNamesAreReservedAndNullKeyIsBad() {
        defaultV1();
        step("APPROVED", ApproverType.ROLE);
        step("REJECTED", ApproverType.ROLE);
        step(null, ApproverType.ROLE);
        assertEquals(List.of("BAD_STEP_KEY:APPROVED", "BAD_STEP_KEY:REJECTED", "BAD_STEP_KEY:null"), validate());
    }

    @Test
    void stepWithoutPositionIsBadStepKey() {
        defaultV1();
        steps.get(2).setPositionNo(null);
        assertEquals(List.of("BAD_STEP_KEY:HEAD"), validate());
    }
}
