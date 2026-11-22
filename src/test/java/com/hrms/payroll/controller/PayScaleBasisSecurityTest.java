package com.hrms.payroll.controller;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.GlobalExceptionHandler;
import com.hrms.payroll.dto.response.PayScaleBasisResponse;
import com.hrms.payroll.service.EmployeeSalaryService;
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
import java.time.LocalDate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The pay-scale basis of an employee's salary carries the amount: HR_ADMIN only, and the date must be a date. */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = PayScaleBasisSecurityTest.TestConfig.class)
class PayScaleBasisSecurityTest {

    private static final String URL = "/api/v1/payroll/employee-salary/5/pay-scale";

    // Not annotated @Configuration on purpose, see EmployeeEndpointSecurityTest.
    @EnableWebMvc
    @EnableMethodSecurity
    static class TestConfig {
        @Bean GlobalExceptionHandler globalExceptionHandler() { return new GlobalExceptionHandler(); }
        @Bean EmployeeSalaryService service() { return mock(EmployeeSalaryService.class); }
        @Bean AuditTrail audit() { return mock(AuditTrail.class); }
        @Bean EmployeeSalaryController controller(EmployeeSalaryService service, AuditTrail audit) {
            return new EmployeeSalaryController(service, audit);
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired EmployeeSalaryService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(service);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void hrAdminGetsTheBasisForTheAskedDate() throws Exception {
        LocalDate day = LocalDate.of(2026, 11, 1);
        when(service.getPayScaleBasis(5L, day)).thenReturn(new PayScaleBasisResponse(
            true, day, 8L, "Grade 8", "الدرجة 8", 1, 3L, "Grade 8", new BigDecimal("460462.00")));

        mvc.perform(get(URL).param("on", "2026-11-01")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.graded").value(true))
            .andExpect(jsonPath("$.data.incrementNo").value(1))
            .andExpect(jsonPath("$.data.structureId").value(3))
            .andExpect(jsonPath("$.data.basicSalary").value(460462.0));
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void aDateThatIsNotADateIsABadRequest() throws Exception {
        mvc.perform(get(URL).param("on", "01/11/2026")).andExpect(status().isBadRequest());
        mvc.perform(get(URL)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "HR_MANAGER")
    void hrManagerIsForbidden() throws Exception {
        mvc.perform(get(URL).param("on", "2026-11-01")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void anEmployeeIsForbidden_evenForThemselves() throws Exception {
        mvc.perform(get(URL).param("on", "2026-11-01")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
