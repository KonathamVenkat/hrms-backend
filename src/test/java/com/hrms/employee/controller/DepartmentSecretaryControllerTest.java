package com.hrms.employee.controller;

import com.hrms.common.exception.GlobalExceptionHandler;
import com.hrms.employee.dto.response.DepartmentSecretaryResponse;
import com.hrms.employee.service.DepartmentSecretaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.OptimisticLockingFailureException;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Naming a department secretary is HR work: HR_ADMIN and HR_MANAGER only; a stale department is a 409. */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = DepartmentSecretaryControllerTest.TestConfig.class)
class DepartmentSecretaryControllerTest {

    private static final String PATH = "/api/v1/employees/5/department-secretary";

    // Not annotated @Configuration on purpose, see EmployeeEndpointSecurityTest.
    @EnableWebMvc
    @EnableMethodSecurity
    static class TestConfig {
        @Bean GlobalExceptionHandler globalExceptionHandler() { return new GlobalExceptionHandler(); }
        @Bean DepartmentSecretaryService service() { return mock(DepartmentSecretaryService.class); }
        @Bean DepartmentSecretaryController controller(DepartmentSecretaryService service) {
            return new DepartmentSecretaryController(service);
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired DepartmentSecretaryService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.reset(service);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private void assertForbidden() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isForbidden());
        mvc.perform(put(PATH).contentType("application/json").content("{\"secretary\":true}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    private void assertAllowed() throws Exception {
        when(service.set(5L, true)).thenReturn(new DepartmentSecretaryResponse(10L, "IT", "IT", true, 5L, "Jane Doe"));
        mvc.perform(put(PATH).contentType("application/json").content("{\"secretary\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.secretary").value(true));
        mvc.perform(get(PATH)).andExpect(status().isOk());
        verify(service).set(5L, true);
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeIsForbidden() throws Exception {
        assertForbidden();
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void systemAdminIsForbidden() throws Exception {
        assertForbidden();
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void hrAdminIsAllowed() throws Exception {
        assertAllowed();
    }

    @Test
    @WithMockUser(roles = "HR_MANAGER")
    void hrManagerIsAllowed() throws Exception {
        assertAllowed();
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void missingSecretaryFlagIsABadRequest() throws Exception {
        mvc.perform(put(PATH).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void staleDepartmentVersionIsAConflict() throws Exception {
        when(service.set(5L, true)).thenThrow(new OptimisticLockingFailureException("stale"));

        mvc.perform(put(PATH).contentType("application/json").content("{\"secretary\":true}"))
            .andExpect(status().isConflict());
    }
}
