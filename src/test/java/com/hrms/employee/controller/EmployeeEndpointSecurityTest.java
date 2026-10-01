package com.hrms.employee.controller;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.GlobalExceptionHandler;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.response.IdentityInfoResponse;
import com.hrms.employee.service.EmployeeService;
import com.hrms.employee.service.IdentityInfoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Checks the role rules ({@code @PreAuthorize}) and the way the shared exception handler turns
 * access/business/not-found errors into HTTP statuses for the employee endpoints. The security
 * filter chain itself (JWT) is not part of this test.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = EmployeeEndpointSecurityTest.TestConfig.class)
class EmployeeEndpointSecurityTest {

    // Deliberately not annotated @Configuration: the application component-scans com.hrms.employee.controller
    // and would otherwise pick this class up (and its mock beans) in EmployeeApplicationTests.
    @EnableWebMvc
    @EnableMethodSecurity
    static class TestConfig {
        @Bean EmployeeService employeeService() { return mock(EmployeeService.class); }
        @Bean IdentityInfoService identityInfoService() { return mock(IdentityInfoService.class); }
        @Bean EmployeeAccessGuard accessGuard() { return mock(EmployeeAccessGuard.class); }
        @Bean GlobalExceptionHandler globalExceptionHandler() { return new GlobalExceptionHandler(); }

        @Bean EmployeeController employeeController(EmployeeService s, EmployeeAccessGuard g) {
            return new EmployeeController(s, g);
        }
        @Bean EmployeeIdentityInfoController identityController(IdentityInfoService s, EmployeeAccessGuard g) {
            return new EmployeeIdentityInfoController(s, g);
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired EmployeeService employeeService;
    @Autowired IdentityInfoService identityService;
    @Autowired EmployeeAccessGuard accessGuard;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(employeeService, identityService, accessGuard);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private static final String IDENTITY_BODY = "{\"nationalId\":\"123456789\"}";

    // ── Identity: read ──────────────────────────────────────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeReadingSomeoneElsesIdentityIsForbidden() throws Exception {
        doThrow(new AccessDeniedException("own records only")).when(accessGuard).assertSelfOrPrivileged(8L);

        mvc.perform(get("/api/v1/employees/8/identity"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(identityService);
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeCanReadTheirOwnIdentity() throws Exception {
        when(identityService.getIdentityInfo(7L)).thenReturn(
            IdentityInfoResponse.builder().employeeId(7L).nationalId("123456789").masked(false).build());

        mvc.perform(get("/api/v1/employees/7/identity"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nationalId").value("123456789"))
            .andExpect(jsonPath("$.data.masked").value(false));
        verify(accessGuard).assertSelfOrPrivileged(7L);
    }

    @Test
    @WithMockUser(roles = "HR_MANAGER")
    void hrManagerReadsIdentityButGetsMaskedValues() throws Exception {
        when(identityService.getIdentityInfo(7L)).thenReturn(
            IdentityInfoResponse.builder().employeeId(7L).nationalId("*****6789").masked(true).build());

        mvc.perform(get("/api/v1/employees/7/identity"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nationalId").value("*****6789"))
            .andExpect(jsonPath("$.data.masked").value(true));
    }

    // ── Identity: write ─────────────────────────────────────

    @Test
    @WithMockUser(roles = "HR_MANAGER")
    void hrManagerCannotWriteIdentityData() throws Exception {
        mvc.perform(put("/api/v1/employees/7/identity")
                .contentType(MediaType.APPLICATION_JSON).content(IDENTITY_BODY))
            .andExpect(status().isForbidden());
        verifyNoInteractions(identityService);
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeCannotWriteIdentityDataEvenForThemself() throws Exception {
        mvc.perform(put("/api/v1/employees/7/identity")
                .contentType(MediaType.APPLICATION_JSON).content(IDENTITY_BODY))
            .andExpect(status().isForbidden());
        verifyNoInteractions(identityService);
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void hrAdminCanWriteIdentityData() throws Exception {
        when(identityService.saveIdentityInfo(anyLong(), any())).thenReturn(
            IdentityInfoResponse.builder().employeeId(7L).nationalId("123456789").masked(false).build());

        mvc.perform(put("/api/v1/employees/7/identity")
                .contentType(MediaType.APPLICATION_JSON).content(IDENTITY_BODY))
            .andExpect(status().isOk());
        verify(identityService).saveIdentityInfo(anyLong(), any());
    }

    // ── Employee endpoints: role rules ──────────────────────

    @Test
    @WithMockUser(roles = "HR_MANAGER")
    void hrManagerCannotDeactivateOrReactivate() throws Exception {
        mvc.perform(delete("/api/v1/employees/5")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/employees/5/reactivate")).andExpect(status().isForbidden());
        verifyNoInteractions(employeeService);
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeCannotListOrLookUpEmployees() throws Exception {
        mvc.perform(get("/api/v1/employees")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/employees/lookup")).andExpect(status().isForbidden());
        verifyNoInteractions(employeeService);
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void hrAdminDeactivatesWithAnExitStatus() throws Exception {
        mvc.perform(delete("/api/v1/employees/5").param("exitStatus", "RESIGNED"))
            .andExpect(status().isOk());
        verify(employeeService).deactivateEmployee(eq(5L), any());
    }

    // ── Shared exception handler mapping ────────────────────

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void businessRuleViolationsAreNotServerErrors() throws Exception {
        doThrow(new BusinessRuleException("CANNOT_DEACTIVATE_SELF", "You cannot deactivate your own account."))
            .when(employeeService).deactivateEmployee(eq(5L), any());

        mvc.perform(delete("/api/v1/employees/5"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void uniqueConstraintViolationIsAConflict() throws Exception {
        doThrow(new org.springframework.dao.DataIntegrityViolationException("could not execute statement",
                new java.sql.SQLException("ORA-00001: unique constraint (HRMS.UQ_EMPLOYEES_WORK_EMAIL) violated")))
            .when(employeeService).deactivateEmployee(eq(5L), any());

        mvc.perform(delete("/api/v1/employees/5"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Work email address is already registered."));
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void unexpectedErrorsReturnAGenericMessage() throws Exception {
        doThrow(new IllegalStateException("secret internal detail"))
            .when(employeeService).deactivateEmployee(eq(5L), any());

        mvc.perform(delete("/api/v1/employees/5"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret internal detail"))));
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void unsupportedMethodIsNotAServerError() throws Exception {
        mvc.perform(patch("/api/v1/employees/5/identity")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void missingEmployeeIsNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Employee", "id", 5L))
            .when(employeeService).deactivateEmployee(eq(5L), any());

        mvc.perform(delete("/api/v1/employees/5")).andExpect(status().isNotFound());
    }
}
