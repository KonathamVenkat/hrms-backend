package com.hrms.employee.controller;

import com.hrms.common.exception.GlobalExceptionHandler;
import com.hrms.employee.service.BankBranchService;
import com.hrms.employee.service.BankService;
import com.hrms.employee.service.CompanyBranchService;
import com.hrms.employee.service.CompanyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bank, bank branch, company and company branch maintenance is SYSTEM_ADMIN only: every verb on every
 * controller must refuse HR_ADMIN and EMPLOYEE and let SYSTEM_ADMIN through. The JWT filter chain is not part
 * of this test (same setup as EmployeeEndpointSecurityTest).
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = MasterAdminControllersSecurityTest.TestConfig.class)
class MasterAdminControllersSecurityTest {

    // Not annotated @Configuration on purpose, see EmployeeEndpointSecurityTest.
    @EnableWebMvc
    @EnableMethodSecurity
    static class TestConfig {
        @Bean GlobalExceptionHandler globalExceptionHandler() { return new GlobalExceptionHandler(); }
        @Bean BankAdminController bank() { return new BankAdminController(mock(BankService.class)); }
        @Bean BankBranchAdminController bankBranch() { return new BankBranchAdminController(mock(BankBranchService.class)); }
        @Bean CompanyAdminController company() { return new CompanyAdminController(mock(CompanyService.class)); }
        @Bean CompanyBranchAdminController companyBranch() { return new CompanyBranchAdminController(mock(CompanyBranchService.class)); }
    }

    private static final String BODY =
        "{\"code\":\"A1\",\"name\":\"n\",\"nameAr\":\"n\",\"bankId\":1,\"companyId\":1,\"version\":0}";

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    /** Calls every endpoint; a refused caller gets 403 on all of them, an allowed one gets 200 (201 on create). */
    private void everyVerb(String path, int expected) throws Exception {
        String base = "/api/v1/admin/" + path;
        int created = expected == 200 ? 201 : expected;
        mvc.perform(get(base)).andExpect(status().is(expected));
        mvc.perform(get(base + "/1")).andExpect(status().is(expected));
        mvc.perform(post(base).contentType("application/json").content(BODY)).andExpect(status().is(created));
        mvc.perform(put(base + "/1").contentType("application/json").content(BODY)).andExpect(status().is(expected));
        mvc.perform(patch(base + "/1/deactivate")).andExpect(status().is(expected));
        mvc.perform(patch(base + "/1/activate")).andExpect(status().is(expected));
        mvc.perform(delete(base + "/1")).andExpect(status().is(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"banks", "bank-branches", "companies", "company-branches"})
    @WithMockUser(roles = "HR_ADMIN")
    void hrAdminIsForbidden(String path) throws Exception {
        everyVerb(path, 403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"banks", "bank-branches", "companies", "company-branches"})
    @WithMockUser(roles = "EMPLOYEE")
    void employeeIsForbidden(String path) throws Exception {
        everyVerb(path, 403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"banks", "bank-branches", "companies", "company-branches"})
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void systemAdminIsAllowed(String path) throws Exception {
        everyVerb(path, 200);
    }
}
