package com.hrms.employee.controller;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.exception.GlobalExceptionHandler;
import com.hrms.employee.dto.response.EmployeeCompletenessResponse;
import com.hrms.employee.service.EmployeeCompletenessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Who may read the completeness of a profile: HR staff for anyone, an employee for their own record only. */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = EmployeeCompletenessSecurityTest.TestConfig.class)
class EmployeeCompletenessSecurityTest {

    // Not annotated @Configuration on purpose, see EmployeeEndpointSecurityTest.
    @EnableWebMvc
    @EnableMethodSecurity
    static class TestConfig {
        @Bean GlobalExceptionHandler globalExceptionHandler() { return new GlobalExceptionHandler(); }
        @Bean("employeeAccessGuard") EmployeeAccessGuard accessGuard() { return mock(EmployeeAccessGuard.class); }
        @Bean EmployeeCompletenessService service() { return mock(EmployeeCompletenessService.class); }
        @Bean EmployeeCompletenessController controller(EmployeeCompletenessService s, EmployeeAccessGuard g) {
            return new EmployeeCompletenessController(s, g);
        }
    }

    private static final String URL = "/api/v1/employees/10/completeness";

    @Autowired WebApplicationContext context;
    @Autowired EmployeeAccessGuard accessGuard;
    @Autowired EmployeeCompletenessService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(accessGuard, service);
        when(accessGuard.isSelfOrPrivileged(anyLong())).thenReturn(true);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private static final EmployeeCompletenessResponse ANSWER =
        new EmployeeCompletenessResponse(true, false, true, true, false, false, true, false);

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void hrAdminGetsEveryFlagIncludingBankAndDocuments() throws Exception {
        when(accessGuard.isHrAdmin()).thenReturn(true);
        when(service.getCompleteness(10L)).thenReturn(ANSWER);

        mvc.perform(get(URL)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.job").value(true))
            .andExpect(jsonPath("$.data.addresses").value(false))
            .andExpect(jsonPath("$.data.bank").value(true))
            .andExpect(jsonPath("$.data.documents").value(false))
            .andExpect(jsonPath("$.data.qualifications").value(true));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void everyoneElseDoesNotLearnWhetherBankOrDocumentsExist() throws Exception {
        when(accessGuard.isHrAdmin()).thenReturn(false);
        when(service.getCompleteness(10L)).thenReturn(ANSWER);

        mvc.perform(get(URL)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.job").value(true))
            .andExpect(jsonPath("$.data.bank").doesNotExist())
            .andExpect(jsonPath("$.data.documents").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void anotherEmployeesRecordIsForbiddenAndNeverReachesTheService() throws Exception {
        when(accessGuard.isSelfOrPrivileged(anyLong())).thenReturn(false);

        mvc.perform(get(URL)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
