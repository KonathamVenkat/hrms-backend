package com.hrms.employee.controller;

import com.hrms.common.exception.GlobalExceptionHandler;
import com.hrms.employee.dto.response.DepartmentHeadResponse;
import com.hrms.employee.service.DepartmentHeadService;
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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Naming a department head is HR work: HR_ADMIN and HR_MANAGER only, and the body must say true or false. */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = DepartmentHeadControllerSecurityTest.TestConfig.class)
class DepartmentHeadControllerSecurityTest {

    private static final String PATH = "/api/v1/employees/5/department-head";

    // Not annotated @Configuration on purpose, see EmployeeEndpointSecurityTest.
    @EnableWebMvc
    @EnableMethodSecurity
    static class TestConfig {
        @Bean GlobalExceptionHandler globalExceptionHandler() { return new GlobalExceptionHandler(); }
        @Bean DepartmentHeadService service() { return mock(DepartmentHeadService.class); }
        @Bean DepartmentHeadController controller(DepartmentHeadService service) {
            return new DepartmentHeadController(service);
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired DepartmentHeadService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.reset(service);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private void assertForbidden() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isForbidden());
        mvc.perform(put(PATH).contentType("application/json").content("{\"head\":true}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    private void assertAllowed() throws Exception {
        when(service.set(5L, true)).thenReturn(new DepartmentHeadResponse(10L, "IT", "IT", true, 5L, "Jane Doe"));
        mvc.perform(put(PATH).contentType("application/json").content("{\"head\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.head").value(true));
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
    void missingHeadFlagIsABadRequest() throws Exception {
        mvc.perform(put(PATH).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
    }
}
