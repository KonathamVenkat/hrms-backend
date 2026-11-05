package com.hrms.auth.security;

import com.hrms.auth.entity.AuthUser;
import com.hrms.auth.repository.AuthUserRepository;
import com.hrms.common.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class EmployeeAccessGuardTest {

    @Mock AuthUserRepository authUserRepository;
    EmployeeAccessGuard guard;

    @BeforeEach
    void setUp() {
        guard = new EmployeeAccessGuard(authUserRepository);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String username, String role, Long linkedEmployeeId) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        lenient().when(authUserRepository.findByUsernameOrEmail(username))
            .thenReturn(Optional.of(AuthUser.builder().username(username).employeeId(linkedEmployeeId).build()));
    }

    // ── assertSelfOrPrivileged ──────────────────────────────

    @Test
    void hrAdminAndHrManagerMayActOnAnyEmployee() {
        loginAs("admin", "HR_ADMIN", 1L);
        assertDoesNotThrow(() -> guard.assertSelfOrPrivileged(99L));

        loginAs("manager", "HR_MANAGER", 2L);
        assertDoesNotThrow(() -> guard.assertSelfOrPrivileged(99L));
    }

    @Test
    void employeeMayActOnTheirOwnRecord() {
        loginAs("emp", "EMPLOYEE", 7L);
        assertDoesNotThrow(() -> guard.assertSelfOrPrivileged(7L));
    }

    @Test
    void employeeCannotActOnSomeoneElsesRecord() {
        loginAs("emp", "EMPLOYEE", 7L);
        assertThrows(AccessDeniedException.class, () -> guard.assertSelfOrPrivileged(8L));
    }

    @Test
    void accountNotLinkedToAnEmployeeIsDenied() {
        loginAs("ghost", "EMPLOYEE", null);
        assertThrows(AccessDeniedException.class, () -> guard.assertSelfOrPrivileged(7L));
    }

    @Test
    void noAuthenticationIsDenied() {
        assertThrows(AccessDeniedException.class, () -> guard.assertSelfOrPrivileged(7L));
    }

    // ── isSelfOrAdmin / isSelfOrPrivileged (used in @PreAuthorize) ──

    @Test
    void hrAdminMayChangeAnyEmployeesRecords() {
        loginAs("admin", "HR_ADMIN", 1L);
        assertTrue(guard.isSelfOrAdmin(99L));
    }

    @Test
    void anEmployeeMayChangeOnlyTheirOwnRecords() {
        loginAs("emp", "EMPLOYEE", 7L);
        assertTrue(guard.isSelfOrAdmin(7L));
        assertFalse(guard.isSelfOrAdmin(8L));
    }

    @Test
    void anHrManagerMayChangeTheirOwnRecordsButNotSomeoneElses() {
        loginAs("manager", "HR_MANAGER", 2L);
        assertTrue(guard.isSelfOrAdmin(2L));
        assertFalse(guard.isSelfOrAdmin(99L));
    }

    @Test
    void anAccountNotLinkedToAnEmployeeMayChangeNothing() {
        loginAs("ghost", "EMPLOYEE", null);
        assertFalse(guard.isSelfOrAdmin(7L));
        assertFalse(guard.isSelfOrPrivileged(7L));
    }

    @Test
    void withoutAuthenticationNothingIsAllowed() {
        assertFalse(guard.isSelfOrAdmin(7L));
        assertFalse(guard.isSelfOrPrivileged(7L));
    }

    @Test
    void anAnonymousCallerIsNeverSignedInEvenThoughSpringCallsItAuthenticated() {
        SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.AnonymousAuthenticationToken(
            "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        lenient().when(authUserRepository.findByUsernameOrEmail("anonymousUser"))
            .thenReturn(Optional.of(AuthUser.builder().username("anonymousUser").employeeId(7L).build()));

        assertFalse(guard.isSelfOrAdmin(7L));
        assertFalse(guard.isSelfOrPrivileged(7L));
    }

    @Test
    void hrStaffMayReadAnyRecordAndAnEmployeeOnlyTheirOwn() {
        loginAs("manager", "HR_MANAGER", 2L);
        assertTrue(guard.isSelfOrPrivileged(99L));

        loginAs("emp", "EMPLOYEE", 7L);
        assertTrue(guard.isSelfOrPrivileged(7L));
        assertFalse(guard.isSelfOrPrivileged(8L));
    }

    // ── canViewUnmasked ─────────────────────────────────────

    @Test
    void onlyHrAdminAndTheEmployeeThemselfSeeUnmaskedValues() {
        loginAs("admin", "HR_ADMIN", 1L);
        assertTrue(guard.canViewUnmasked(99L));

        loginAs("emp", "EMPLOYEE", 7L);
        assertTrue(guard.canViewUnmasked(7L));
        assertFalse(guard.canViewUnmasked(8L));
    }

    @Test
    void hrManagerSeesMaskedValuesForOtherEmployees() {
        loginAs("manager", "HR_MANAGER", 2L);
        assertFalse(guard.canViewUnmasked(99L));
    }

    @Test
    void unauthenticatedCallerNeverSeesUnmaskedValues() {
        assertFalse(guard.canViewUnmasked(7L));
    }

    // ── isSoleActiveHolderOf ─────────────────────────────────

    private void linkedLogin(long employeeId, UserRole role, boolean active) {
        lenient().when(authUserRepository.findByEmployeeId(employeeId)).thenReturn(Optional.of(
            AuthUser.builder().employeeId(employeeId).role(role).isActive(active).build()));
    }

    @Test
    void soleHolderWhenTheEmployeesActiveLoginHasTheRoleAndNoOtherActiveLoginDoes() {
        linkedLogin(7L, UserRole.GENERAL_MANAGER, true);
        lenient().when(authUserRepository.countOtherActiveHolders(UserRole.GENERAL_MANAGER, 7L)).thenReturn(0L);
        assertTrue(guard.isSoleActiveHolderOf("GENERAL_MANAGER", 7L));

        lenient().when(authUserRepository.countOtherActiveHolders(UserRole.GENERAL_MANAGER, 7L)).thenReturn(1L);
        assertFalse(guard.isSoleActiveHolderOf("GENERAL_MANAGER", 7L), "a second GM");
    }

    @Test
    void notSoleHolderWithoutTheRoleAnActiveLoginOrAKnownRole() {
        lenient().when(authUserRepository.countOtherActiveHolders(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyLong())).thenReturn(0L);
        linkedLogin(7L, UserRole.EMPLOYEE, true);
        assertFalse(guard.isSoleActiveHolderOf("GENERAL_MANAGER", 7L), "other role");
        linkedLogin(8L, UserRole.GENERAL_MANAGER, false);
        assertFalse(guard.isSoleActiveHolderOf("GENERAL_MANAGER", 8L), "deactivated login");
        assertFalse(guard.isSoleActiveHolderOf("GENERAL_MANAGER", 9L), "no login");
        assertFalse(guard.isSoleActiveHolderOf("NOT_A_ROLE", 7L), "unknown role");
    }

    @Test
    void currentEmployeeIdIsResolvedServerSide() {
        loginAs("emp", "EMPLOYEE", 7L);
        assertEquals(7L, guard.currentEmployeeId());
    }

    // ── assertMayDecideRequestOf ──────────────────────────────

    private void requesterWithRole(long employeeId, UserRole role) {
        lenient().when(authUserRepository.findByEmployeeId(employeeId)).thenReturn(Optional.of(
            AuthUser.builder().employeeId(employeeId).role(role).isActive(true).build()));
    }

    @Test
    void anEmployeesRequestIsDecidedByHrAdminOrHrManagerAndByNobodyHigher() {
        requesterWithRole(5L, UserRole.EMPLOYEE);
        loginAs("hr", "HR_ADMIN", 20L);
        guard.assertMayDecideRequestOf(5L);
        loginAs("hrm", "HR_MANAGER", 21L);
        guard.assertMayDecideRequestOf(5L);
        for (String role : new String[] {"GENERAL_MANAGER", "CEO", "EMPLOYEE"}) {
            loginAs("u", role, 22L);
            assertEquals("DECIDER_ROLE_REQUIRED", org.junit.jupiter.api.Assertions.assertThrows(
                com.hrms.common.exception.BusinessRuleException.class, () -> guard.assertMayDecideRequestOf(5L)).getRuleCode());
        }
    }

    @Test
    void aRequestFromHrIsDecidedOnlyByTheGeneralManager() {
        requesterWithRole(6L, UserRole.HR_ADMIN);
        requesterWithRole(7L, UserRole.HR_MANAGER);
        loginAs("gm", "GENERAL_MANAGER", 30L);
        guard.assertMayDecideRequestOf(6L);
        guard.assertMayDecideRequestOf(7L);
        for (String role : new String[] {"HR_ADMIN", "HR_MANAGER", "CEO"}) {
            loginAs("u", role, 31L);
            org.junit.jupiter.api.Assertions.assertThrows(com.hrms.common.exception.BusinessRuleException.class,
                () -> guard.assertMayDecideRequestOf(6L), role);
        }
    }

    @Test
    void theGeneralManagersRequestIsDecidedOnlyByTheCeo() {
        requesterWithRole(8L, UserRole.GENERAL_MANAGER);
        loginAs("ceo", "CEO", 40L);
        guard.assertMayDecideRequestOf(8L);
        for (String role : new String[] {"HR_ADMIN", "HR_MANAGER", "GENERAL_MANAGER"}) {
            loginAs("u", role, 41L);
            org.junit.jupiter.api.Assertions.assertThrows(com.hrms.common.exception.BusinessRuleException.class,
                () -> guard.assertMayDecideRequestOf(8L), role);
        }
    }

    @Test
    void aRequesterWithNoLoginCountsAsAnEmployee_andAnonymousCallersAreRefused() {
        lenient().when(authUserRepository.findByEmployeeId(9L)).thenReturn(Optional.empty());
        loginAs("hr", "HR_ADMIN", 20L);
        guard.assertMayDecideRequestOf(9L);

        SecurityContextHolder.clearContext();
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
            () -> guard.assertMayDecideRequestOf(9L));
    }

}
