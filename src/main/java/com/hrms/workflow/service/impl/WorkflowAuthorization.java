package com.hrms.workflow.service.impl;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.workflow.entity.WorkflowStep;
import com.hrms.workflow.enums.ApproverType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/** The only place that decides whether the caller may act on, or override, a workflow step (spec 7.2). */
@Component
@RequiredArgsConstructor
public class WorkflowAuthorization {

    private static final Set<String> OVERSEER_ROLES =
        Set.of("ROLE_HR_ADMIN", "ROLE_HR_MANAGER", "ROLE_GENERAL_MANAGER", "ROLE_CEO");

    private final ApproverResolver resolver;
    private final EmployeeAccessGuard accessGuard;

    /** The caller must hold the step; the requester gets 422 SELF_APPROVAL, anyone else who does not hold it 403. */
    public void assertMayAct(WorkflowStep step, long requesterId, Long targetDepartmentId) {
        Set<String> roles = callerRoles();
        Long caller = accessGuard.currentEmployeeId();
        if (caller != null && caller == requesterId) {
            throw new BusinessRuleException("SELF_APPROVAL", "You cannot act on your own request.");
        }
        if (caller == null || !resolver.holds(step, caller, roles, requesterId, targetDepartmentId)) {
            throw new AccessDeniedException("You are not the approver of this step.");
        }
    }

    /** Whether {@link #assertMayAct} would pass for the caller (no exception); drives what a status offers. */
    public boolean mayAct(WorkflowStep step, long requesterId, Long targetDepartmentId) {
        Set<String> roles = callerRoles();
        Long caller = accessGuard.currentEmployeeId();
        return caller != null && caller != requesterId
            && resolver.holds(step, caller, roles, requesterId, targetDepartmentId);
    }

    /**
     * HR_ADMIN only, for a step nobody but the requester could act on: a person step with nobody resolved, or whose
     * resolved person is the requester (a step the requester holds that cannot be skipped without approving outright
     * would otherwise wait forever), or a ROLE step whose role no active login other than the requester's has (the
     * only General Manager's own leave). A present approver other than the requester is never overridden (there is no
     * absence data; a wider policy is the user's call). The caller needs an employee record (the history row names
     * the actor) and may not be the requester (422 SELF_APPROVAL). Known limit: the only HR_ADMIN whose own request
     * reaches an HR_ADMIN step cannot override it; a second HR_ADMIN login is needed.
     */
    public void assertMayOverride(WorkflowStep step, long requesterId, Long targetDepartmentId) {
        Long caller = accessGuard.currentEmployeeId();
        if (caller == null) {
            throw new AccessDeniedException("Your account is not linked to an employee.");
        }
        if (caller == requesterId) {
            throw new BusinessRuleException("SELF_APPROVAL", "You cannot act on your own request.");
        }
        if (!accessGuard.isHrAdmin() || !nobodyElseCanAct(step, requesterId, targetDepartmentId)) {
            throw new AccessDeniedException("This step cannot be overridden.");
        }
    }

    /** Whether {@link #assertMayOverride} would pass for the caller (no exception); drives what a status offers. */
    public boolean mayOverride(WorkflowStep step, long requesterId, Long targetDepartmentId) {
        if (!accessGuard.isHrAdmin()) {
            return false;
        }
        Long caller = accessGuard.currentEmployeeId();
        return caller != null && caller != requesterId && nobodyElseCanAct(step, requesterId, targetDepartmentId);
    }

    /** The override condition on the step itself, shared by {@link #assertMayOverride} and {@link #mayOverride}. */
    private boolean nobodyElseCanAct(WorkflowStep step, long requesterId, Long targetDepartmentId) {
        if (step.getApproverType() == ApproverType.ROLE) {
            return resolver.roleHolding(step, requesterId)
                .filter(role -> accessGuard.isSoleActiveHolderOf(role, requesterId)).isPresent();
        }
        return resolver.resolve(step, requesterId, targetDepartmentId).filter(id -> id != requesterId).isEmpty();
    }

    /** HR_ADMIN, HR_MANAGER and GENERAL_MANAGER may see every request (spec 10); acting still needs the step. */
    public boolean isOverseer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority).anyMatch(OVERSEER_ROLES::contains);
    }

    /** Whether the caller holds any of these steps for this request, resolved live (never true for the requester). */
    public boolean holdsAny(Collection<WorkflowStep> steps, long requesterId, Long targetDepartmentId) {
        Long caller = accessGuard.currentEmployeeId();
        if (caller == null) {
            return false;
        }
        Set<String> roles = callerRoles();
        return steps.stream().anyMatch(s -> resolver.holds(s, caller, roles, requesterId, targetDepartmentId));
    }

    private static Set<String> callerRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required.");
        }
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }
}
