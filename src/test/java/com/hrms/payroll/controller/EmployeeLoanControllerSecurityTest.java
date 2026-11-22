package com.hrms.payroll.controller;

import com.hrms.common.dto.PagedResponse;
import com.hrms.common.exception.GlobalExceptionHandler;
import com.hrms.payroll.service.EmployeeLoanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Loans carry pay amounts: every call is HR_ADMIN's; bad bodies and filters are 400 before the service. */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = EmployeeLoanControllerSecurityTest.TestConfig.class)
class EmployeeLoanControllerSecurityTest {

    private static final String BASE = "/api/v1/payroll/loans";
    private static final String LOAN = "{\"employeeId\":5,\"componentId\":21,\"principal\":12122140,"
        + "\"instalmentCount\":36,\"startMonth\":\"2026-12\"}";
    private static final String REASON = "{\"version\":0,\"reason\":\"repaid\"}";

    // Not annotated @Configuration on purpose, see EmployeeEndpointSecurityTest.
    @EnableWebMvc
    @EnableMethodSecurity
    static class TestConfig {
        @Bean GlobalExceptionHandler globalExceptionHandler() { return new GlobalExceptionHandler(); }
        @Bean static MethodValidationPostProcessor methodValidation() { return new MethodValidationPostProcessor(); }
        @Bean EmployeeLoanService service() { return mock(EmployeeLoanService.class); }
        @Bean EmployeeLoanController controller(EmployeeLoanService s) { return new EmployeeLoanController(s); }
    }

    @Autowired WebApplicationContext context;
    @Autowired EmployeeLoanService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(service);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        when(service.search(any(), any(), any(), any(), any())).thenReturn(PagedResponse.from(Page.empty()));
        when(service.loanTypes()).thenReturn(List.of());
    }

    private List<RequestBuilder> everyCall() {
        return List.of(get(BASE), get(BASE + "/types"),
            post(BASE).contentType(MediaType.APPLICATION_JSON).content(LOAN),
            patch(BASE + "/1/close").contentType(MediaType.APPLICATION_JSON).content(REASON),
            patch(BASE + "/1/cancel").contentType(MediaType.APPLICATION_JSON).content(REASON));
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void hrAdminMayDoEverything() throws Exception {
        for (RequestBuilder call : everyCall()) {
            int code = mvc.perform(call).andReturn().getResponse().getStatus();
            assertTrue(code == 200 || code == 201, "expected 2xx, got " + code);
        }
    }

    @Test
    @WithMockUser(roles = {"HR_MANAGER", "EMPLOYEE", "SYSTEM_ADMIN", "GENERAL_MANAGER"})
    void everyOtherRoleIsForbidden() throws Exception {
        for (RequestBuilder call : everyCall()) {
            mvc.perform(call).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void badBodiesAndFiltersAreRefusedBeforeTheService() throws Exception {
        reset(service);
        for (String body : List.of(
                LOAN.replace("\"2026-12\"", "\"2026-13\""),            // not a month
                LOAN.replace("12122140", "0"),                        // no principal
                LOAN.replace("\"instalmentCount\":36", "\"instalmentCount\":361"),
                LOAN.replace(",\"componentId\":21", ""))) {           // no loan type
            mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(patch(BASE + "/1/close").contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"reason\":\" \"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get(BASE).param("status", "DELETED")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
