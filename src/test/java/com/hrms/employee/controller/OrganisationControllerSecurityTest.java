package com.hrms.employee.controller;

import com.hrms.common.exception.GlobalExceptionHandler;
import com.hrms.employee.dto.response.BranchAssignmentResult;
import com.hrms.employee.dto.response.OrganisationSummaryResponse;
import com.hrms.employee.service.OrganisationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Organisation set-up is HR work: HR_ADMIN and HR_MANAGER only; bad bodies are 400 before any service call. */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = OrganisationControllerSecurityTest.TestConfig.class)
class OrganisationControllerSecurityTest {

    private static final String BASE = "/api/v1/organisation";
    private static final String PLACEMENT = "{\"locationId\":7,\"managerEmployeeId\":5,\"version\":3}";
    private static final String ASSIGN = "{\"items\":[{\"employeeId\":11,\"branchId\":1}]}";

    // Not annotated @Configuration on purpose, see EmployeeEndpointSecurityTest.
    @EnableWebMvc
    @EnableMethodSecurity
    static class TestConfig {
        @Bean GlobalExceptionHandler globalExceptionHandler() { return new GlobalExceptionHandler(); }
        @Bean OrganisationService service() { return mock(OrganisationService.class); }
        @Bean OrganisationController controller(OrganisationService service) {
            return new OrganisationController(service);
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired OrganisationService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(service);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        when(service.summary()).thenReturn(new OrganisationSummaryResponse(12, 3, 2));
        when(service.employeesWithoutBranch(any(), any())).thenReturn(Page.empty());
        when(service.managerCandidates(any(), any(), any())).thenReturn(Page.empty());
        when(service.assignBranches(any())).thenReturn(new BranchAssignmentResult(1, List.of()));
    }

    private void assertForbidden() throws Exception {
        mvc.perform(get(BASE + "/branches")).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/branches/lookup")).andExpect(status().isForbidden());
        mvc.perform(put(BASE + "/branches/1/placement").contentType("application/json").content(PLACEMENT))
            .andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/summary")).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/employees-without-branch")).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/manager-candidates")).andExpect(status().isForbidden());
        mvc.perform(put(BASE + "/branch-assignments").contentType("application/json").content(ASSIGN))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    private void assertAllowed() throws Exception {
        mvc.perform(get(BASE + "/branches")).andExpect(status().isOk());
        mvc.perform(get(BASE + "/branches/lookup").param("locationId", "7")).andExpect(status().isOk());
        mvc.perform(put(BASE + "/branches/1/placement").contentType("application/json").content(PLACEMENT))
            .andExpect(status().isOk());
        mvc.perform(get(BASE + "/summary")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.employeesWithoutBranch").value(12));
        mvc.perform(get(BASE + "/employees-without-branch")).andExpect(status().isOk());
        mvc.perform(get(BASE + "/manager-candidates").param("locationId", "7")).andExpect(status().isOk());
        mvc.perform(put(BASE + "/branch-assignments").contentType("application/json").content(ASSIGN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assigned").value(1));
        verify(service).lookupBranches(7L);
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
    void placementNeedsLocationAndVersion() throws Exception {
        mvc.perform(put(BASE + "/branches/1/placement").contentType("application/json").content("{\"version\":3}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put(BASE + "/branches/1/placement").contentType("application/json").content("{\"locationId\":7}"))
            .andExpect(status().isBadRequest());
        verify(service, never()).setPlacement(any(), any());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void assignmentsMustHaveBetweenOneAndTwoHundredRows() throws Exception {
        mvc.perform(put(BASE + "/branch-assignments").contentType("application/json").content("{\"items\":[]}"))
            .andExpect(status().isBadRequest());
        String tooMany = IntStream.rangeClosed(1, 201)
            .mapToObj(i -> "{\"employeeId\":" + i + ",\"branchId\":1}")
            .collect(Collectors.joining(",", "{\"items\":[", "]}"));
        mvc.perform(put(BASE + "/branch-assignments").contentType("application/json").content(tooMany))
            .andExpect(status().isBadRequest());
        mvc.perform(put(BASE + "/branch-assignments").contentType("application/json")
                .content("{\"items\":[{\"employeeId\":11}]}"))
            .andExpect(status().isBadRequest());
        verify(service, never()).assignBranches(any());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void gapListClampsPageAndSize() throws Exception {
        mvc.perform(get(BASE + "/employees-without-branch").param("page", "-3").param("size", "1000"))
            .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).employeesWithoutBranch(isNull(), captor.capture());
        assertEquals(PageRequest.of(0, 100), captor.getValue());
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void managerCandidatesClampPageAndSizeAndPassFilters() throws Exception {
        mvc.perform(get(BASE + "/manager-candidates")
                .param("locationId", "7").param("q", "lual").param("page", "-3").param("size", "1000"))
            .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).managerCandidates(eq(7L), eq("lual"), captor.capture());
        assertEquals(PageRequest.of(0, 100), captor.getValue());
    }
}
