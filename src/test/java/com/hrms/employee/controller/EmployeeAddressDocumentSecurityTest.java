package com.hrms.employee.controller;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.GlobalExceptionHandler;
import com.hrms.employee.service.EmployeeAddressService;
import com.hrms.employee.service.EmployeeDocumentService;
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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may touch the Addresses tab: HR_ADMIN, or the employee for their own record (the guard decides which record is
 * theirs; HR_MANAGER may read any record but change only their own). Documents stay with HR. The JWT filter chain is not part of this test (same setup as EmployeeInfoControllersSecurityTest).
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = EmployeeAddressDocumentSecurityTest.TestConfig.class)
class EmployeeAddressDocumentSecurityTest {

    // Not annotated @Configuration on purpose, see EmployeeEndpointSecurityTest.
    @EnableWebMvc
    @EnableMethodSecurity
    static class TestConfig {
        @Bean GlobalExceptionHandler globalExceptionHandler() { return new GlobalExceptionHandler(); }
        @Bean EmployeeAccessGuard accessGuard() { return mock(EmployeeAccessGuard.class); }
        @Bean EmployeeAddressService addresses() { return mock(EmployeeAddressService.class); }
        @Bean EmployeeDocumentService documents() { return mock(EmployeeDocumentService.class); }
        @Bean AuditTrail audit() { return mock(AuditTrail.class); }
        @Bean EmployeeAddressController addressController(EmployeeAccessGuard g, EmployeeAddressService s) {
            return new EmployeeAddressController(g, s);
        }
        @Bean EmployeeDocumentController documentController(EmployeeDocumentService s, AuditTrail a) {
            return new EmployeeDocumentController(s, a);
        }
    }

    private static final String ADDRESS = "/api/v1/employees/10/addresses";
    private static final String DOCUMENT = "/api/v1/employees/10/documents";
    private static final String ADDRESS_BODY = "{\"addressType\":\"PERMANENT\",\"addressLine1\":\"1 Main St\","
        + "\"city\":\"Juba\",\"country\":\"South Sudan\"}";

    @Autowired WebApplicationContext context;
    @Autowired EmployeeAccessGuard accessGuard;
    @Autowired EmployeeAddressService addressService;
    @Autowired EmployeeDocumentService documentService;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(accessGuard, addressService, documentService);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private void addressWrites(int expected) throws Exception {
        mvc.perform(post(ADDRESS).contentType("application/json").content(ADDRESS_BODY))
            .andExpect(status().is(expected == 200 ? 201 : expected));
        mvc.perform(put(ADDRESS + "/1").contentType("application/json").content(ADDRESS_BODY)).andExpect(status().is(expected));
        mvc.perform(patch(ADDRESS + "/1/set-primary")).andExpect(status().is(expected));
        mvc.perform(delete(ADDRESS + "/1")).andExpect(status().is(expected));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeAddsChangesAndReadsTheirOwnAddresses() throws Exception {
        addressWrites(200);
        mvc.perform(get(ADDRESS)).andExpect(status().isOk());
        verify(accessGuard).assertSelfOrPrivileged(10L);
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeCannotTouchAnotherEmployeesAddresses() throws Exception {
        doThrow(new AccessDeniedException("own records only")).when(accessGuard).assertSelfOrAdmin(anyLong());
        doThrow(new AccessDeniedException("own records only")).when(accessGuard).assertSelfOrPrivileged(anyLong());

        addressWrites(403);
        mvc.perform(get(ADDRESS)).andExpect(status().isForbidden());
        verifyNoInteractions(addressService);
    }

    @Test
    @WithMockUser(roles = "HR_MANAGER")
    void hrManagerReadsAnyAddressButChangesOnlyTheirOwn() throws Exception {
        mvc.perform(get(ADDRESS)).andExpect(status().isOk());
        doThrow(new AccessDeniedException("own records or HR_ADMIN only")).when(accessGuard).assertSelfOrAdmin(anyLong());
        addressWrites(403);
    }

    /** Documents are HR_ADMIN-only to change and HR staff-only to read, even for the employee's own record. */
    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeCannotReadOrChangeDocumentsEvenTheirOwn() throws Exception {
        mvc.perform(get(DOCUMENT)).andExpect(status().isForbidden());
        mvc.perform(get(DOCUMENT + "/1/download")).andExpect(status().isForbidden());
        mvc.perform(delete(DOCUMENT + "/1")).andExpect(status().isForbidden());
        mvc.perform(patch(DOCUMENT + "/1/verify")).andExpect(status().isForbidden());
        verifyNoInteractions(documentService);
    }
}
