package com.hrms.employee.service;

import com.hrms.auth.entity.AuthUser;
import com.hrms.auth.repository.AuthUserRepository;
import com.hrms.auth.repository.RefreshTokenRepository;
import com.hrms.common.enums.UserRole;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.service.impl.EmployeeLoginAccounts;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class EmployeeLoginAccountsTest {

    private final AuthUserRepository authUsers = mock(AuthUserRepository.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final EmployeeLoginAccounts accounts = new EmployeeLoginAccounts(authUsers, mock(PasswordEncoder.class), refreshTokens);

    @Test
    void anUnknownRoleIsRejectedInsteadOfIgnored() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> accounts.changeRole(Employee.builder().id(5L).build(), "SUPERUSER"));
        assertEquals("INVALID_ROLE", ex.getRuleCode());
        verifyNoInteractions(authUsers);
    }

    private static AuthUser login(long userId, long employeeId, UserRole role) {
        return AuthUser.builder().userId(userId).employeeId(employeeId).role(role).isActive(true).build();
    }

    @Test
    void leavingEndsEverySessionAndComingBackNeedsANewPassword() {
        AuthUser user = login(77, 5, UserRole.EMPLOYEE);
        user.setIsLocked(true);
        user.setFailedAttempts(3);
        user.setMustChangePassword(0);
        when(authUsers.findByEmployeeId(5L)).thenReturn(Optional.of(user));

        accounts.setActive(5L, false, "admin");
        assertFalse(user.getIsActive());
        assertEquals(0, user.getMustChangePassword()); // leaving changes no credential, it only revokes the sessions
        verify(refreshTokens).revokeAllByUserId(77L);

        accounts.setActive(5L, true, "admin");
        assertTrue(user.getIsActive());
        assertEquals(1, user.getMustChangePassword());
        assertEquals(0, user.getFailedAttempts());
        assertFalse(user.getIsLocked());
        verify(refreshTokens, org.mockito.Mockito.times(2)).revokeAllByUserId(77L);
    }

    @Test
    void roleChangeReportsWhetherAnythingChanged() {
        AuthUser user = login(77, 5, UserRole.EMPLOYEE);
        when(authUsers.findByEmployeeId(5L)).thenReturn(Optional.of(user));
        Employee employee = Employee.builder().id(5L).build();

        assertFalse(accounts.changeRole(employee, "EMPLOYEE"));
        assertTrue(accounts.changeRole(employee, "HR_MANAGER"));
        assertEquals(UserRole.HR_MANAGER, user.getRole());
    }

    @Test
    void onlyAnHrAdminIsCheckedForTheLastAdminRule() {
        when(authUsers.findByEmployeeId(5L)).thenReturn(Optional.of(login(77, 5, UserRole.HR_MANAGER)));

        accounts.requireAnotherActiveAdmin(5L); // a manager can always be deactivated

        org.mockito.Mockito.verify(authUsers, org.mockito.Mockito.never()).findActiveByRoleForUpdate(UserRole.HR_ADMIN);
    }

    @Test
    void theLastAdminIsRefusedAndAnAdminWithAPeerIsNot() {
        AuthUser target = login(77, 5, UserRole.HR_ADMIN);
        AuthUser peer = login(78, 6, UserRole.HR_ADMIN);
        when(authUsers.findByEmployeeId(5L)).thenReturn(Optional.of(target));

        when(authUsers.findActiveByRoleForUpdate(UserRole.HR_ADMIN)).thenReturn(List.of(target));
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> accounts.requireAnotherActiveAdmin(5L));
        assertEquals("LAST_HR_ADMIN", ex.getRuleCode());

        when(authUsers.findActiveByRoleForUpdate(UserRole.HR_ADMIN)).thenReturn(List.of(target, peer));
        accounts.requireAnotherActiveAdmin(5L); // no exception
    }
}
