package com.hrms.workflow.service.impl;

import com.hrms.workflow.entity.WorkflowRoute;
import com.hrms.workflow.entity.WorkflowStep;
import com.hrms.workflow.enums.ApproverType;
import com.hrms.workflow.enums.RequestType;
import com.hrms.workflow.enums.StepOutcome;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Publish-time checks of spec 5.4 on a flow held as steps and routes. Returns error codes; empty means publishable.
 * Pure: no database, no Flowable.
 * Codes are plain ("LOOP") except for problems that name a step: UNREACHABLE_STEP:KEY (a step no path reaches, a
 * duplicate step key, or a route whose step is not in the flow; the key is "null" for a route with no step) and
 * BAD_STEP_KEY:KEY (a key that is not [A-Z][A-Z0-9_]{0,29}, a reserved terminal name APPROVED / REJECTED, or a step
 * with no position). A flow with a bad step key is reported on those alone: the other checks need sound keys.
 */
@Component
public class WorkflowValidator {

    static final String NO_ROUTE_FOR_OUTCOME = "NO_ROUTE_FOR_OUTCOME";
    static final String UNREACHABLE_STEP = "UNREACHABLE_STEP";
    static final String LOOP = "LOOP";
    static final String MISSING_OTHERWISE_ROUTE = "MISSING_OTHERWISE_ROUTE";
    static final String OVERLAPPING_CONDITIONS = "OVERLAPPING_CONDITIONS";
    static final String NO_APPROVING_STEP = "NO_APPROVING_STEP";
    static final String UNKNOWN_CONDITION_FIELD = "UNKNOWN_CONDITION_FIELD";
    static final String BAD_CONDITION_VALUE = "BAD_CONDITION_VALUE";
    static final String SECRETARY_CANNOT_DECIDE = "SECRETARY_CANNOT_DECIDE";
    static final String BAD_STEP_KEY = "BAD_STEP_KEY";

    /** Step keys become BPMN element ids, so only plain identifiers (the generator checks again). */
    static final Pattern STEP_KEY = Pattern.compile("[A-Z][A-Z0-9_]{0,29}");

    static final String APPROVED = "APPROVED";
    static final String REJECTED = "REJECTED";

    /** Condition fields an admin may use, per request type. Only leave has one so far. */
    static final Map<RequestType, Set<String>> CONDITION_FIELDS = Map.of(
        RequestType.LEAVE, Set.of("totalDays"));
    private static final int MAX_DECIMALS = 3;
    private static final int MAX_INTEGER_DIGITS = 11;
    static final Set<String> OPERATORS = Set.of(">", ">=", "<", "<=", "=", "<>");

    public List<String> validate(List<WorkflowStep> steps, List<WorkflowRoute> routes, RequestType type) {
        List<String> badKeys = badStepKeys(steps);
        if (!badKeys.isEmpty()) {
            return badKeys;
        }
        Set<String> errors = new LinkedHashSet<>();
        Map<String, List<WorkflowRoute>> byStep = new LinkedHashMap<>();
        for (WorkflowStep s : steps) {
            if (byStep.putIfAbsent(s.getStepKey(), new ArrayList<>()) != null) {
                errors.add(UNREACHABLE_STEP + ":" + s.getStepKey()); // duplicate step key
            }
        }
        for (WorkflowRoute r : routes) {
            String key = r.getStep() == null ? null : r.getStep().getStepKey();
            if (key == null || !byStep.containsKey(key)) {
                errors.add(UNREACHABLE_STEP + ":" + key); // route of a step that is not in the flow
            } else {
                byStep.get(key).add(r);
            }
        }

        checkRoutesExist(steps, byStep, errors);
        checkConditions(routes, type, errors);
        checkOtherwiseAndOverlap(byStep, errors);
        Set<String> reachable = checkGraph(steps, byStep, errors);
        boolean approves = reachable.stream().flatMap(k -> byStep.get(k).stream())
            .anyMatch(r -> r.getOutcome() == StepOutcome.APPROVE && APPROVED.equals(r.getTarget()));
        if (!approves) {
            errors.add(NO_APPROVING_STEP);
        }
        return new ArrayList<>(errors);
    }

    private List<String> badStepKeys(List<WorkflowStep> steps) {
        return steps.stream()
            .filter(s -> s.getStepKey() == null || !STEP_KEY.matcher(s.getStepKey()).matches()
                || APPROVED.equals(s.getStepKey()) || REJECTED.equals(s.getStepKey()) || s.getPositionNo() == null)
            .map(s -> BAD_STEP_KEY + ":" + s.getStepKey()).distinct().toList();
    }

    /**
     * Every step needs a route. A secretary step must forward (and may not decide, see below); every other step must
     * be able to reject and to approve or forward.
     */
    private void checkRoutesExist(List<WorkflowStep> steps, Map<String, List<WorkflowRoute>> byStep, Set<String> errors) {
        for (WorkflowStep step : steps) {
            List<WorkflowRoute> own = byStep.get(step.getStepKey());
            boolean danglingTarget = own.stream().anyMatch(r -> !isTarget(r.getTarget(), byStep));
            boolean secretary = step.getApproverType() == ApproverType.DEPARTMENT_SECRETARY;
            boolean forwards = has(own, StepOutcome.FORWARD);
            boolean complete = secretary
                ? forwards
                : has(own, StepOutcome.REJECT) && (has(own, StepOutcome.APPROVE) || forwards);
            if (own.isEmpty() || danglingTarget || !complete) {
                errors.add(NO_ROUTE_FOR_OUTCOME);
            }
            if (secretary && (has(own, StepOutcome.APPROVE) || has(own, StepOutcome.REJECT))) {
                errors.add(SECRETARY_CANNOT_DECIDE);
            }
        }
    }

    private boolean has(List<WorkflowRoute> own, StepOutcome outcome) {
        return own.stream().anyMatch(r -> r.getOutcome() == outcome);
    }

    private boolean isTarget(String target, Map<String, List<WorkflowRoute>> byStep) {
        return APPROVED.equals(target) || REJECTED.equals(target) || byStep.containsKey(target);
    }

    private void checkConditions(List<WorkflowRoute> routes, RequestType type, Set<String> errors) {
        for (WorkflowRoute r : routes) {
            if (!isConditional(r)) {
                continue;
            }
            if (r.getCondField() == null || r.getCondOperator() == null || r.getCondValue() == null) {
                errors.add(BAD_CONDITION_VALUE); // a condition is all three parts or none
                continue;
            }
            if (!CONDITION_FIELDS.getOrDefault(type, Set.of()).contains(r.getCondField())) {
                errors.add(UNKNOWN_CONDITION_FIELD);
            }
            if (!OPERATORS.contains(r.getCondOperator()) || parse(r.getCondValue()).isEmpty()) {
                errors.add(BAD_CONDITION_VALUE);
            }
        }
    }

    private boolean isConditional(WorkflowRoute r) {
        return r.getCondField() != null || r.getCondOperator() != null || r.getCondValue() != null;
    }

    /** A finite number that fits NUMBER(14,3): up to 3 decimals and 11 integer digits ("abc", "NaN", "1E20" fail). */
    private Optional<BigDecimal> parse(String text) {
        try {
            if (text == null) {
                return Optional.empty();
            }
            BigDecimal value = new BigDecimal(text.trim());
            BigDecimal t = value.stripTrailingZeros();
            boolean fits = t.scale() <= MAX_DECIMALS && t.precision() - t.scale() <= MAX_INTEGER_DIGITS;
            return fits ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Per step and outcome: conditional routes need a last unconditional "otherwise" and must not overlap. */
    private void checkOtherwiseAndOverlap(Map<String, List<WorkflowRoute>> byStep, Set<String> errors) {
        for (List<WorkflowRoute> own : byStep.values()) {
            for (StepOutcome outcome : StepOutcome.values()) {
                List<WorkflowRoute> group = own.stream().filter(r -> r.getOutcome() == outcome)
                    .sorted(Comparator.comparing(WorkflowRoute::getPositionNo)).toList();
                checkGroup(group, errors);
            }
        }
    }

    private void checkGroup(List<WorkflowRoute> group, Set<String> errors) {
        List<WorkflowRoute> conditional = group.stream().filter(this::isConditional).toList();
        long unconditional = group.size() - conditional.size();
        if (unconditional > 1) {
            errors.add(OVERLAPPING_CONDITIONS);
        }
        if (!conditional.isEmpty()
            && (unconditional != 1 || isConditional(group.get(group.size() - 1)))) {
            errors.add(MISSING_OTHERWISE_ROUTE);
        }
        // ponytail: interval reasoning on one numeric field; routes on different fields are not compared
        // (a second field would need a real satisfiability check). LEAVE has a single field today.
        for (int i = 0; i < conditional.size(); i++) {
            for (int j = i + 1; j < conditional.size(); j++) {
                if (overlap(conditional.get(i), conditional.get(j))) {
                    errors.add(OVERLAPPING_CONDITIONS);
                }
            }
        }
    }

    private boolean overlap(WorkflowRoute a, WorkflowRoute b) {
        if (a.getCondField() == null || !a.getCondField().equals(b.getCondField())) {
            return false;
        }
        for (Range x : ranges(a)) {
            for (Range y : ranges(b)) {
                if (x.intersects(y)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The values of the field that make the condition true, as intervals (null bound = unbounded). */
    private List<Range> ranges(WorkflowRoute r) {
        Optional<BigDecimal> parsed = parse(r.getCondValue());
        if (parsed.isEmpty() || r.getCondOperator() == null || !OPERATORS.contains(r.getCondOperator())) {
            return List.of(); // already reported as BAD_CONDITION_VALUE
        }
        BigDecimal v = parsed.get();
        return switch (r.getCondOperator()) {
            case ">" -> List.of(new Range(v, false, null, false));
            case ">=" -> List.of(new Range(v, true, null, false));
            case "<" -> List.of(new Range(null, false, v, false));
            case "<=" -> List.of(new Range(null, false, v, true));
            case "=" -> List.of(new Range(v, true, v, true));
            default -> List.of(new Range(null, false, v, false), new Range(v, false, null, false)); // <>
        };
    }

    private record Range(BigDecimal lo, boolean loIn, BigDecimal hi, boolean hiIn) {
        boolean intersects(Range o) {
            BigDecimal low = lo;
            boolean lowIn = loIn;
            if (o.lo != null && (low == null || o.lo.compareTo(low) > 0)) {
                low = o.lo;
                lowIn = o.loIn;
            } else if (o.lo != null && o.lo.compareTo(low) == 0) {
                lowIn = loIn && o.loIn;
            }
            BigDecimal high = hi;
            boolean highIn = hiIn;
            if (o.hi != null && (high == null || o.hi.compareTo(high) < 0)) {
                high = o.hi;
                highIn = o.hiIn;
            } else if (o.hi != null && o.hi.compareTo(high) == 0) {
                highIn = hiIn && o.hiIn;
            }
            if (low == null || high == null) {
                return true;
            }
            int c = low.compareTo(high);
            return c < 0 || (c == 0 && lowIn && highIn);
        }
    }

    /** Returns the step keys reachable from the first step; reports unreachable steps and loops. */
    private Set<String> checkGraph(List<WorkflowStep> steps, Map<String, List<WorkflowRoute>> byStep, Set<String> errors) {
        Set<String> seen = new HashSet<>();
        if (steps.isEmpty()) {
            return seen;
        }
        String first = steps.stream().min(Comparator.comparing(WorkflowStep::getPositionNo)).orElseThrow().getStepKey();
        visit(first, byStep, seen);
        byStep.keySet().stream().filter(k -> !seen.contains(k)).forEach(k -> errors.add(UNREACHABLE_STEP + ":" + k));
        if (hasCycle(first, byStep, new HashSet<>(), new HashSet<>())) {
            errors.add(LOOP);
        }
        return seen;
    }

    private void visit(String key, Map<String, List<WorkflowRoute>> byStep, Set<String> seen) {
        if (!byStep.containsKey(key) || !seen.add(key)) {
            return;
        }
        byStep.get(key).forEach(r -> visit(r.getTarget(), byStep, seen));
    }

    private boolean hasCycle(String key, Map<String, List<WorkflowRoute>> byStep, Set<String> path, Set<String> done) {
        if (!byStep.containsKey(key) || done.contains(key)) {
            return false;
        }
        if (!path.add(key)) {
            return true;
        }
        for (WorkflowRoute r : byStep.get(key)) {
            if (hasCycle(r.getTarget(), byStep, path, done)) {
                return true;
            }
        }
        path.remove(key);
        done.add(key);
        return false;
    }
}
