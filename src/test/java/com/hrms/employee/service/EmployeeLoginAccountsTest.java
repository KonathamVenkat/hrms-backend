package com.hrms.employee.service;

import com.hrms.auth.repository.AuthUserRepository;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.service.impl.EmployeeLoginAccounts;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class EmployeeLoginAccountsTest {

    private final AuthUserRepository authUsers = mock(AuthUserRepository.class);
    private final EmployeeLoginAccounts accounts = new EmployeeLoginAccounts(authUsers, mock(PasswordEncoder.class));

    @Test
    void anUnknownRoleIsRejectedInsteadOfIgnored() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> accounts.changeRole(Employee.builder().id(5L).build(), "SUPERUSER"));
        assertEquals("INVALID_ROLE", ex.getRuleCode());
        verifyNoInteractions(authUsers);
    }
}
