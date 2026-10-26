package com.hrms.workflow.service.impl;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.service.OrganisationLookup;
import com.hrms.workflow.enums.RequestType;
import com.hrms.workflow.repository.WorkflowSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Input checks of the workflow actions: the per-type switch, the target department, condition facts, free text. */
@Component
@RequiredArgsConstructor
class WorkflowInputs {

    static final String FLAG_PREFIX = "WORKFLOW_ENABLED_";
    static final int MAX_TEXT = 500;

    private final OrganisationLookup organisation;
    private final WorkflowSettingRepository settings;

    /** WORKFLOW_SETTINGS WORKFLOW_ENABLED_&lt;TYPE&gt; must be 'true'; missing counts as off. */
    boolean isEnabled(RequestType type) {
        return settings.findById(FLAG_PREFIX + type.name())
            .map(s -> s.getSettingValue() != null && "true".equalsIgnoreCase(s.getSettingValue().trim())).orElse(false);
    }

    void requireEnabled(RequestType type) {
        if (!isEnabled(type)) {
            throw new BusinessRuleException("WORKFLOW_DISABLED", "The approval workflow is off for " + type + ".");
        }
    }

    void requireActiveDepartment(long departmentId) {
        if (!organisation.departmentExists(departmentId)) {
            throw new ResourceNotFoundException("Department", "id", departmentId);
        }
        if (!organisation.isDepartmentActive(departmentId)) {
            throw new BusinessRuleException("DEPARTMENT_INACTIVE", "The chosen department is not active.");
        }
    }

    /**
     * Exactly the type's allow-listed condition facts, as BigDecimal (Flowable fails on a missing variable, and only
     * allow-listed values may become process variables). A wrong set is the calling module's bug.
     */
    static Map<String, Object> variables(RequestType type, Map<String, Number> facts) {
        Set<String> fields = WorkflowValidator.CONDITION_FIELDS.getOrDefault(type, Set.of());
        Map<String, Number> given = facts == null ? Map.of() : facts;
        given.keySet().stream().filter(k -> !fields.contains(k)).findFirst().ifPresent(k -> {
            throw new IllegalArgumentException("Unknown workflow fact " + k + " for " + type);
        });
        Map<String, Object> variables = new HashMap<>();
        for (String field : fields) {
            Number n = given.get(field);
            if (n == null) {
                throw new IllegalArgumentException("Missing workflow fact " + field + " for " + type);
            }
            variables.put(field, n instanceof BigDecimal b ? b : new BigDecimal(n.toString()));
        }
        return variables;
    }

    /** Trimmed; blank is null; over the column length is refused rather than cut. */
    static String text(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String t = value.trim();
        if (t.length() > MAX_TEXT) {
            throw new BusinessRuleException("TEXT_TOO_LONG", "At most " + MAX_TEXT + " characters.");
        }
        return t;
    }
}
