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
}
