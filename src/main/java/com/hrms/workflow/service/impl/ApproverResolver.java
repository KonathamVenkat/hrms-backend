package com.hrms.workflow.service.impl;

import com.hrms.auth.security.ApprovalRouting;
import com.hrms.workflow.enums.ApproverType;
import com.hrms.common.enums.UserRole;

import com.hrms.employee.service.OrganisationLookup;
import com.hrms.workflow.entity.WorkflowStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Who may act on a step, resolved live from the organisation (never stored), so replacing a head or manager takes
 * effect at once. Pure lookups, no writes.
 */
@Component
@RequiredArgsConstructor
public class ApproverResolver {

    private final OrganisationLookup organisation;

    /**
     * The one person who holds a person step: BRANCH_MANAGER from the requester's branch, DEPARTMENT_SECRETARY and
     * DEPARTMENT_HEAD from the target department (null target = nobody). Always empty for ROLE steps, which are
     * held by every holder of the role, not one person. Empty = nobody.
     */
    public Optional<Long> resolve(WorkflowStep step, long requesterEmployeeId, Long targetDepartmentId) {
        return switch (step.getApproverType()) {
            case BRANCH_MANAGER -> organisation.branchManagerOf(requesterEmployeeId);
            case DEPARTMENT_SECRETARY -> targetDepartmentId == null
                ? Optional.empty() : organisation.secretaryOf(targetDepartmentId);
            case DEPARTMENT_HEAD -> targetDepartmentId == null
                ? Optional.empty() : organisation.headOf(targetDepartmentId);
            case ROLE -> Optional.empty();
        };
    }

    /**
     * The role that holds a ROLE step for this requester: the step's own role, except that a request from a member of
     * that group passes to the level above (General Manager's own request: CEO; HR_ADMIN's or HR_MANAGER's request at an
     * HR step: General Manager). Empty for a person step. See {@link ApprovalRouting#stepRole}.
     */
    public Optional<String> roleHolding(WorkflowStep step, long requesterEmployeeId) {
        if (step.getApproverType() != ApproverType.ROLE || step.getApproverRole() == null) {
            return Optional.empty();
        }
        UserRole requesterRole = organisation.accountRoleOf(requesterEmployeeId)
            .flatMap(name -> {
                try {
                    return Optional.of(UserRole.valueOf(name));
                } catch (IllegalArgumentException e) {
                    return Optional.<UserRole>empty();
                }
            }).orElse(UserRole.EMPLOYEE);
        return Optional.of(ApprovalRouting.stepRole(step.getApproverRole(), requesterRole));
    }

    /** The requester's own current active department (default target for a requester who picks it themselves). */
    public Optional<Long> departmentOf(long employeeId) {
        return organisation.departmentOf(employeeId);
    }

    /**
     * Whether the caller holds the step. The requester never does (self-approval). Roles are authority names with the
     * ROLE_ prefix. Pure: no SecurityContext access.
     */
    public boolean holds(WorkflowStep step, long callerEmployeeId, Set<String> callerRoles,
                         long requesterEmployeeId, Long targetDepartmentId) {
        if (callerEmployeeId == requesterEmployeeId) {
            return false;
        }
        switch (step.getApproverType()) {
            case ROLE -> {
                return roleHolding(step, requesterEmployeeId)
                    .filter(role -> callerRoles.contains("ROLE_" + role)).isPresent();
            }
            default -> {
                return resolve(step, requesterEmployeeId, targetDepartmentId)
                    .filter(id -> id == callerEmployeeId).isPresent();
            }
        }
    }
}
