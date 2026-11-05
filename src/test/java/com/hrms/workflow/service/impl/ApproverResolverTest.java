package com.hrms.workflow.service.impl;

import com.hrms.employee.service.OrganisationLookup;
import com.hrms.workflow.entity.WorkflowStep;
import com.hrms.workflow.enums.ApproverType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** Live approver resolution: branch from the requester, secretary/head from the target department, roles by name. */
@ExtendWith(MockitoExtension.class)
class ApproverResolverTest {

    @Mock OrganisationLookup organisation;
    @InjectMocks ApproverResolver resolver;

    static WorkflowStep step(ApproverType type, String role) {
        WorkflowStep s = new WorkflowStep();
        s.setApproverType(type);
        s.setApproverRole(role);
        return s;
    }

    static final WorkflowStep BRANCH = step(ApproverType.BRANCH_MANAGER, null);
    static final WorkflowStep SECRETARY = step(ApproverType.DEPARTMENT_SECRETARY, null);
    static final WorkflowStep HEAD = step(ApproverType.DEPARTMENT_HEAD, null);
    static final WorkflowStep GM = step(ApproverType.ROLE, "GENERAL_MANAGER");
    static final WorkflowStep HR_ADMIN = step(ApproverType.ROLE, "HR_ADMIN");

    @Test
    void branchStepResolvesToTheManagerOfTheRequestersBranch() {
        when(organisation.branchManagerOf(1L)).thenReturn(Optional.of(10L));

        assertEquals(Optional.of(10L), resolver.resolve(BRANCH, 1L, 99L));
    }

    @Test
    void requesterWithoutBranchOrBranchWithoutManagerResolvesToNobody() {
        when(organisation.branchManagerOf(1L)).thenReturn(Optional.empty());

        assertTrue(resolver.resolve(BRANCH, 1L, null).isEmpty());
    }

    @Test
    void secretaryAndHeadResolveFromTheTargetDepartment() {
        when(organisation.secretaryOf(7L)).thenReturn(Optional.of(30L));
        when(organisation.headOf(7L)).thenReturn(Optional.of(40L));

        assertEquals(Optional.of(30L), resolver.resolve(SECRETARY, 1L, 7L));
        assertEquals(Optional.of(40L), resolver.resolve(HEAD, 1L, 7L));
    }

    @Test
    void secretaryAndHeadWithoutATargetDepartmentResolveToNobody() {
        assertTrue(resolver.resolve(SECRETARY, 1L, null).isEmpty());
        assertTrue(resolver.resolve(HEAD, 1L, null).isEmpty());
    }

    @Test
    void roleStepsResolveToNobodyBecauseTheRoleIsHeldByManyPeople() {
        assertTrue(resolver.resolve(GM, 1L, 7L).isEmpty());
    }

    @Test
    void replacedHeadIsResolvedLiveNewHeadHoldsOldHeadDoesNot() {
        Set<String> roles = Set.of("ROLE_EMPLOYEE");
        when(organisation.headOf(7L)).thenReturn(Optional.of(40L));
        assertTrue(resolver.holds(HEAD, 40L, roles, 1L, 7L));

        when(organisation.headOf(7L)).thenReturn(Optional.of(41L));

        assertFalse(resolver.holds(HEAD, 40L, roles, 1L, 7L), "old head");
        assertTrue(resolver.holds(HEAD, 41L, roles, 1L, 7L), "new head");
    }

    @Test
    void requesterNeverHoldsAStepEvenWhenResolvedAsItsPerson() {
        Set<String> roles = Set.of("ROLE_EMPLOYEE");
        when(organisation.branchManagerOf(1L)).thenReturn(Optional.of(1L));
        when(organisation.headOf(7L)).thenReturn(Optional.of(1L));
        when(organisation.secretaryOf(7L)).thenReturn(Optional.of(1L));

        assertEquals(Optional.of(1L), resolver.resolve(BRANCH, 1L, 7L), "requester really is resolved");
        assertEquals(Optional.of(1L), resolver.resolve(HEAD, 1L, 7L), "requester really is resolved");
        assertEquals(Optional.of(1L), resolver.resolve(SECRETARY, 1L, 7L), "requester really is resolved");
        assertFalse(resolver.holds(BRANCH, 1L, roles, 1L, 7L));
        assertFalse(resolver.holds(HEAD, 1L, roles, 1L, 7L));
        assertFalse(resolver.holds(SECRETARY, 1L, roles, 1L, 7L));
        assertFalse(resolver.holds(HR_ADMIN, 1L, Set.of("ROLE_HR_ADMIN"), 1L, 7L), "even an HR admin on their own request");
    }

    @Test
    void generalManagerHoldsOnlyTheGmRoleStep() {
        Set<String> roles = Set.of("ROLE_GENERAL_MANAGER");

        assertTrue(resolver.holds(GM, 5L, roles, 1L, 7L));
        assertFalse(resolver.holds(HR_ADMIN, 5L, roles, 1L, 7L));
    }

    @Test
    void hrManagerDoesNotHoldAnHrAdminStep() {
        assertFalse(resolver.holds(HR_ADMIN, 5L, Set.of("ROLE_HR_MANAGER"), 1L, 7L));
    }

    @Test
    void roleStepIsHeldByAnyoneWithTheRoleRegardlessOfOrganisation() {
        assertTrue(resolver.holds(HR_ADMIN, 5L, Set.of("ROLE_HR_ADMIN"), 1L, null));
    }

    // ── the role that holds a ROLE step depends on who asked ──

    private void requesterIs(long employeeId, String role) {
        when(organisation.accountRoleOf(employeeId)).thenReturn(Optional.of(role));
    }

    @Test
    void theGeneralManagersOwnRequestAtTheGmStepIsHeldByTheCeoAndNotByAnotherGeneralManager() {
        requesterIs(1L, "GENERAL_MANAGER");

        assertEquals(Optional.of("CEO"), resolver.roleHolding(GM, 1L));
        assertTrue(resolver.holds(GM, 5L, Set.of("ROLE_CEO"), 1L, null));
        assertFalse(resolver.holds(GM, 6L, Set.of("ROLE_GENERAL_MANAGER"), 1L, null));
        assertFalse(resolver.holds(GM, 7L, Set.of("ROLE_HR_ADMIN"), 1L, null));
    }

    @Test
    void anHrAdminsOrHrManagersRequestAtAnHrStepIsHeldByTheGeneralManager() {
        requesterIs(1L, "HR_ADMIN");
        requesterIs(2L, "HR_MANAGER");

        assertEquals(Optional.of("GENERAL_MANAGER"), resolver.roleHolding(HR_ADMIN, 1L));
        assertEquals(Optional.of("GENERAL_MANAGER"), resolver.roleHolding(HR_ADMIN, 2L));
        assertTrue(resolver.holds(HR_ADMIN, 5L, Set.of("ROLE_GENERAL_MANAGER"), 1L, null));
        assertFalse(resolver.holds(HR_ADMIN, 6L, Set.of("ROLE_HR_ADMIN"), 1L, null));
    }

    @Test
    void anOrdinaryEmployeesRequestAndAPersonStepAreUnchanged() {
        requesterIs(1L, "EMPLOYEE");
        assertEquals(Optional.of("GENERAL_MANAGER"), resolver.roleHolding(GM, 1L));
        assertEquals(Optional.of("HR_ADMIN"), resolver.roleHolding(HR_ADMIN, 1L));
        assertTrue(resolver.roleHolding(BRANCH, 1L).isEmpty());
        // no login on record counts as an employee
        assertEquals(Optional.of("HR_ADMIN"), resolver.roleHolding(HR_ADMIN, 99L));
    }

}
