package com.hrms.employee.controller;

import com.hrms.common.exception.GlobalExceptionHandler;
import com.hrms.employee.dto.response.GradeScaleStepResponse;
import com.hrms.employee.service.GradeService;
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

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The pay scale carries salary amounts: HR_ADMIN only; every other role is refused before the service is called. */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = GradeScaleControllerSecurityTest.TestConfig.class)
class GradeScaleControllerSecurityTest {

    private static final String URL = "/api/v1/payroll/grade-scale";

    // Not annotated @Configuration on purpose, see EmployeeEndpointSecurityTest.
    @EnableWebMvc
    @EnableMethodSecurity
    static class TestConfig {
        @Bean GlobalExceptionHandler globalExceptionHandler() { return new GlobalExceptionHandler(); }
        @Bean GradeService service() { return mock(GradeService.class); }
        @Bean GradeScaleController controller(GradeService service) { return new GradeScaleController(service); }
    }

    @Autowired WebApplicationContext context;
    @Autowired GradeService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(service);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void hrAdminGetsTheScale() throws Exception {
        when(service.getPayScale()).thenReturn(List.of(new GradeScaleStepResponse(
            8L, "GRADE_08", "Grade 8", "الدرجة 8", 1, new BigDecimal("460462.000"), "2026-03-01")));

        mvc.perform(get(URL)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].gradeId").value(8))
            .andExpect(jsonPath("$.data[0].incrementNo").value(1))
            .andExpect(jsonPath("$.data[0].startingScale").value(460462.0))
            .andExpect(jsonPath("$.data[0].effectiveFrom").value("2026-03-01"));
    }

    @Test
    @WithMockUser(roles = "HR_MANAGER")
    void hrManagerIsForbidden() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeIsForbidden() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void systemAdminIsForbidden() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
