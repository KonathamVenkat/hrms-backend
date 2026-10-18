package com.hrms.employee.controller;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.exception.GlobalExceptionHandler;
import com.hrms.employee.service.BankBranchService;
import com.hrms.employee.service.BankService;
import com.hrms.employee.service.EmployeeBankAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may touch employee bank accounts: anyone signed in may ask (the guard then limits an employee to their own
 * record), only HR_ADMIN may change them or use the bank dropdowns. The JWT filter chain is not part of this test.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = EmployeeBankAccountSecurityTest.TestConfig.class)
class EmployeeBankAccountSecurityTest {

    // Not annotated @Configuration on purpose, see EmployeeEndpointSecurityTest.
    @EnableWebMvc
    @EnableMethodSecurity
    static class TestConfig {
        @Bean GlobalExceptionHandler globalExceptionHandler() { return new GlobalExceptionHandler(); }
        @Bean EmployeeAccessGuard accessGuard() { return mock(EmployeeAccessGuard.class); }
        @Bean EmployeeBankAccountService accountService() { return mock(EmployeeBankAccountService.class); }
        @Bean EmployeeBankAccountController accounts(EmployeeBankAccountService s, EmployeeAccessGuard g) {
            return new EmployeeBankAccountController(s, g);
        }
        @Bean BankLookupController lookups() {
            return new BankLookupController(mock(BankService.class), mock(BankBranchService.class));
        }
    }

    private static final String BODY = "{\"bankId\":1,\"accountNumber\":\"12345678\",\"version\":0}";
    private static final String BASE = "/api/v1/employees/10/bank-accounts";

    @Autowired WebApplicationContext context;
    @Autowired EmployeeAccessGuard accessGuard;
    @Autowired EmployeeBankAccountService accountService;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(accessGuard, accountService);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private void writes(int expected) throws Exception {
        mvc.perform(post(BASE).contentType("application/json").content(BODY)).andExpect(status().is(expected == 200 ? 201 : expected));
        mvc.perform(put(BASE + "/1").contentType("application/json").content(BODY)).andExpect(status().is(expected));
        mvc.perform(patch(BASE + "/1/set-primary")).andExpect(status().is(expected));
        mvc.perform(delete(BASE + "/1")).andExpect(status().is(expected));
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void hrAdminMayChangeAccounts() throws Exception {
        writes(200);
        mvc.perform(get(BASE)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "HR_MANAGER")
    void hrManagerMayReadButNotChange() throws Exception {
        writes(403);
        mvc.perform(get(BASE)).andExpect(status().isOk());
        verify(accountService, never()).addAccount(any(), any());
        verify(accountService, never()).updateAccount(any(), any(), any());
        verify(accountService, never()).setPrimary(any(), any());
        verify(accountService, never()).deactivateAccount(any(), any());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeMayNotChangeAccountsAndReadsOnlyTheirOwn() throws Exception {
        writes(403);

        mvc.perform(get(BASE)).andExpect(status().isOk()); // the guard lets their own record through
        doThrow(new AccessDeniedException("own records only")).when(accessGuard).assertSelfOrPrivileged(anyLong());
        mvc.perform(get(BASE)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR_MANAGER")
    void bankDropdownsAreForHrAdminOnly() throws Exception {
        mvc.perform(get("/api/v1/banks/lookup")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/banks/1/branches/lookup")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void hrAdminGetsTheBankDropdowns() throws Exception {
        mvc.perform(get("/api/v1/banks/lookup")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/banks/1/branches/lookup")).andExpect(status().isOk());
    }
}
