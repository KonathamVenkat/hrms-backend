package com.hrms.workflow.service.impl;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.employee.service.OrganisationLookup;
import com.hrms.workflow.entity.WorkflowStep;
import com.hrms.workflow.enums.ApproverType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** Server-side step authorization: caller identity and roles come from the security context, never a parameter. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowAuthorizationTest {

    @Mock OrganisationLookup organisation;
    @Mock EmployeeAccessGuard guard;
    WorkflowAuthorization authorization;

    static final WorkflowStep BRANCH = ApproverResolverTest.step(ApproverType.BRANCH_MANAGER, null);
    static final WorkflowStep HEAD = ApproverResolverTest.step(ApproverType.DEPARTMENT_HEAD, null);
    static final WorkflowStep GM = ApproverResolverTest.step(ApproverType.ROLE, "GENERAL_MANAGER");

    @BeforeEach
    void setUp() {
        authorization = new WorkflowAuthorization(new ApproverResolver(organisation), guard);
        // requester 1 is in branch A (manager 10); requester 2 is in branch B (manager 20)
        when(organisation.branchManagerOf(1L)).thenReturn(Optional.of(10L));
        when(organisation.branchManagerOf(2L)).thenReturn(Optional.of(20L));
        when(organisation.headOf(7L)).thenReturn(Optional.of(40L));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void caller(Long employeeId, String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "user", "n/a", AuthorityUtils.createAuthorityList(roles)));
        when(guard.currentEmployeeId()).thenReturn(employeeId);
        when(guard.isHrAdmin()).thenReturn(java.util.Arrays.asList(roles).contains("ROLE_HR_ADMIN"));
    }

    @Test
    void branchManagerMayActOnARequestFromTheirOwnBranch() {
        caller(10L, "ROLE_EMPLOYEE");

        assertDoesNotThrow(() -> authorization.assertMayAct(BRANCH, 1L, null));
    }

    @Test
    void managerOfBranchACannotActOnARequestFromBranchB() {
        caller(10L, "ROLE_EMPLOYEE");

        assertThrows(AccessDeniedException.class, () -> authorization.assertMayAct(BRANCH, 2L, null));
    }

    @Test
    void employeeWhoIsNobodysApproverIsDenied() {
        caller(99L, "ROLE_EMPLOYEE");

        assertThrows(AccessDeniedException.class, () -> authorization.assertMayAct(BRANCH, 1L, 7L));
        assertThrows(AccessDeniedException.class, () -> authorization.assertMayAct(HEAD, 1L, 7L));
        assertThrows(AccessDeniedException.class, () -> authorization.assertMayAct(GM, 1L, 7L));
    }

    @Test
    void requesterGetsSelfApprovalConflictEvenWithAnApproverRole() {
        caller(1L, "ROLE_EMPLOYEE", "ROLE_GENERAL_MANAGER");

        BusinessRuleException e = assertThrows(BusinessRuleException.class,
            () -> authorization.assertMayAct(GM, 1L, 7L));
        assertEquals("SELF_APPROVAL", e.getRuleCode());
    }

    @Test
    void callerWithoutAnEmployeeRecordIsDenied() {
        caller(null, "ROLE_GENERAL_MANAGER");

        assertThrows(AccessDeniedException.class, () -> authorization.assertMayAct(GM, 1L, 7L));
    }

    @Test
    void generalManagerMayActOnTheGmStep() {
        caller(5L, "ROLE_GENERAL_MANAGER");

        assertDoesNotThrow(() -> authorization.assertMayAct(GM, 1L, 7L));
    }

    @Test
    void hrAdminMayOverrideAPersonStepOnlyWhenNobodyIsResolved() {
        caller(3L, "ROLE_HR_ADMIN");
        when(organisation.headOf(8L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> authorization.assertMayOverride(HEAD, 1L, 8L));
        assertThrows(AccessDeniedException.class, () -> authorization.assertMayOverride(HEAD, 1L, 7L), "head exists");
        assertThrows(AccessDeniedException.class, () -> authorization.assertMayOverride(BRANCH, 1L, null), "manager exists");
    }

    @Test
    void hrAdminMayOverrideAPersonStepHeldByTheRequesterButNotBySomeoneElse() {
        caller(3L, "ROLE_HR_ADMIN");
        when(organisation.headOf(8L)).thenReturn(Optional.of(1L)); // the requester heads department 8

        assertDoesNotThrow(() -> authorization.assertMayOverride(HEAD, 1L, 8L));
        assertThrows(AccessDeniedException.class, () -> authorization.assertMayOverride(HEAD, 1L, 7L), "head 40");
    }

    @Test
    void theRequesterHoldingTheStepStillGetsSelfApprovalOnOverride() {
        caller(1L, "ROLE_HR_ADMIN");
        when(organisation.headOf(8L)).thenReturn(Optional.of(1L));

        BusinessRuleException e = assertThrows(BusinessRuleException.class,
            () -> authorization.assertMayOverride(HEAD, 1L, 8L));
        assertEquals("SELF_APPROVAL", e.getRuleCode());
    }

    @Test
    void overseersAreHrAdminHrManagerAndGeneralManagerOnly() {
        for (String role : new String[] {"ROLE_HR_ADMIN", "ROLE_HR_MANAGER", "ROLE_GENERAL_MANAGER"}) {
            caller(4L, role);
            assertTrue(authorization.isOverseer(), role);
        }
        for (String role : new String[] {"ROLE_EMPLOYEE", "ROLE_SYSTEM_ADMIN"}) {
            caller(4L, role);
            assertFalse(authorization.isOverseer(), role);
        }
    }

    @Test
    void holdsAnyIsTrueOnlyForAHolderOfOneOfTheSteps() {
        caller(40L, "ROLE_EMPLOYEE"); // head of department 7
        assertTrue(authorization.holdsAny(java.util.List.of(BRANCH, HEAD), 1L, 7L));
        assertFalse(authorization.holdsAny(java.util.List.of(BRANCH, GM), 1L, 7L));
        caller(1L, "ROLE_GENERAL_MANAGER"); // the requester never holds
        assertFalse(authorization.holdsAny(java.util.List.of(GM), 1L, 7L));
    }

    @Test
    void overrideIsRefusedForRoleStepsAndForNonAdmins() {
        caller(3L, "ROLE_HR_ADMIN");
        assertThrows(AccessDeniedException.class, () -> authorization.assertMayOverride(GM, 1L, 7L), "role step");

        caller(4L, "ROLE_HR_MANAGER");
        when(organisation.headOf(8L)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> authorization.assertMayOverride(HEAD, 1L, 8L), "not HR_ADMIN");
    }

    @Test
    void generalManagerHrManagerAndSystemAdminCannotOverride() {
        when(organisation.headOf(8L)).thenReturn(Optional.empty());
        for (String role : new String[] {"ROLE_GENERAL_MANAGER", "ROLE_HR_MANAGER", "ROLE_SYSTEM_ADMIN"}) {
            caller(4L, role);
            assertThrows(AccessDeniedException.class, () -> authorization.assertMayOverride(HEAD, 1L, 8L), role);
        }
    }

    @Test
    void hrAdminWhoIsTheRequesterGetsSelfApprovalOnOverride() {
        caller(1L, "ROLE_HR_ADMIN");
        when(organisation.headOf(8L)).thenReturn(Optional.empty());

        BusinessRuleException e = assertThrows(BusinessRuleException.class,
            () -> authorization.assertMayOverride(HEAD, 1L, 8L));
        assertEquals("SELF_APPROVAL", e.getRuleCode());
    }

    @Test
    void hrAdminWithoutAnEmployeeRecordCannotOverride() {
        caller(null, "ROLE_HR_ADMIN");
        when(organisation.headOf(8L)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> authorization.assertMayOverride(HEAD, 1L, 8L));
    }

    @Test
    void unauthenticatedCallerIsDeniedOnOverrideAndAct() {
        assertThrows(AccessDeniedException.class, () -> authorization.assertMayOverride(HEAD, 1L, 8L));
        assertThrows(AccessDeniedException.class, () -> authorization.assertMayAct(GM, 1L, 7L));
    }
}
