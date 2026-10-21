package com.hrms.employee.controller;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.exception.GlobalExceptionHandler;
import com.hrms.employee.service.EducationLevelService;
import com.hrms.employee.service.EmployeeDependentService;
import com.hrms.employee.service.EmployeeEducationService;
import com.hrms.employee.service.EmployeeExperienceService;
import com.hrms.employee.service.EmployeeLanguageService;
import com.hrms.employee.service.EmployeeReferenceService;
import com.hrms.employee.service.EmployeeTrainingService;
import com.hrms.employee.service.LanguageLevelService;
import com.hrms.employee.service.LanguageService;
import com.hrms.employee.service.RelationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may touch the six employee-information tabs: anyone signed in may read (the guard then limits an employee to
 * their own record), only HR_ADMIN may add, change, deactivate or activate, and only HR_ADMIN may use the dropdowns.
 * The JWT filter chain is not part of this test (same setup as EmployeeBankAccountSecurityTest).
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = EmployeeInfoControllersSecurityTest.TestConfig.class)
class EmployeeInfoControllersSecurityTest {

    // Not annotated @Configuration on purpose, see EmployeeEndpointSecurityTest.
    @EnableWebMvc
    @EnableMethodSecurity
    static class TestConfig {
        @Bean GlobalExceptionHandler globalExceptionHandler() { return new GlobalExceptionHandler(); }
        @Bean EmployeeAccessGuard accessGuard() { return mock(EmployeeAccessGuard.class); }
        @Bean EmployeeDependentService dependants() { return mock(EmployeeDependentService.class); }
        @Bean EmployeeEducationService education() { return mock(EmployeeEducationService.class); }
        @Bean EmployeeTrainingService training() { return mock(EmployeeTrainingService.class); }
        @Bean EmployeeLanguageService languages() { return mock(EmployeeLanguageService.class); }
        @Bean EmployeeExperienceService experience() { return mock(EmployeeExperienceService.class); }
        @Bean EmployeeReferenceService references() { return mock(EmployeeReferenceService.class); }
        @Bean EmployeeDependentController c1(EmployeeDependentService s, EmployeeAccessGuard g) { return new EmployeeDependentController(s, g); }
        @Bean EmployeeEducationController c2(EmployeeEducationService s, EmployeeAccessGuard g) { return new EmployeeEducationController(s, g); }
        @Bean EmployeeTrainingController c3(EmployeeTrainingService s, EmployeeAccessGuard g) { return new EmployeeTrainingController(s, g); }
        @Bean EmployeeLanguageController c4(EmployeeLanguageService s, EmployeeAccessGuard g) { return new EmployeeLanguageController(s, g); }
        @Bean EmployeeExperienceController c5(EmployeeExperienceService s, EmployeeAccessGuard g) { return new EmployeeExperienceController(s, g); }
        @Bean EmployeeReferenceController c6(EmployeeReferenceService s, EmployeeAccessGuard g) { return new EmployeeReferenceController(s, g); }
        @Bean ReferenceLookupController lookups() {
            return new ReferenceLookupController(mock(RelationService.class), mock(LanguageService.class),
                mock(LanguageLevelService.class), mock(EducationLevelService.class));
        }
    }

    static Stream<String> sections() {
        return Stream.of("dependants", "education", "training", "languages", "experience", "references");
    }

    /** One body that satisfies every section's required fields. */
    private static final String BODY = "{\"relationId\":1,\"firstName\":\"A\",\"qualification\":\"q\",\"courseName\":\"c\","
        + "\"languageId\":1,\"employerName\":\"e\",\"version\":0}";

    @Autowired WebApplicationContext context;
    @Autowired EmployeeAccessGuard accessGuard;
    @Autowired EmployeeDependentService dependants;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(accessGuard, dependants);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private static String base(String section) {
        return "/api/v1/employees/10/" + section;
    }

    /** Every writing verb: a refused caller gets 403, an allowed one gets 200 (201 on add). */
    private void writes(String section, int expected) throws Exception {
        mvc.perform(post(base(section)).contentType("application/json").content(BODY))
            .andExpect(status().is(expected == 200 ? 201 : expected));
        mvc.perform(put(base(section) + "/1").contentType("application/json").content(BODY)).andExpect(status().is(expected));
        mvc.perform(patch(base(section) + "/1/deactivate")).andExpect(status().is(expected));
        mvc.perform(patch(base(section) + "/1/activate")).andExpect(status().is(expected));
    }

    @ParameterizedTest
    @MethodSource("sections")
    @WithMockUser(roles = "HR_ADMIN")
    void hrAdminMayReadAndChange(String section) throws Exception {
        writes(section, 200);
        mvc.perform(get(base(section))).andExpect(status().isOk());
    }

    @ParameterizedTest
    @MethodSource("sections")
    @WithMockUser(roles = "HR_MANAGER")
    void hrManagerMayReadButNotChange(String section) throws Exception {
        writes(section, 403);
        mvc.perform(get(base(section))).andExpect(status().isOk());
    }

    @ParameterizedTest
    @MethodSource("sections")
    @WithMockUser(roles = "EMPLOYEE")
    void employeeMayNotChangeAndReadsOnlyTheirOwn(String section) throws Exception {
        writes(section, 403);

        mvc.perform(get(base(section))).andExpect(status().isOk()); // the guard lets their own record through
        doThrow(new AccessDeniedException("own records only")).when(accessGuard).assertSelfOrPrivileged(anyLong());
        mvc.perform(get(base(section))).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void aRefusedEmployeeNeverReachesTheService() throws Exception {
        doThrow(new AccessDeniedException("own records only")).when(accessGuard).assertSelfOrPrivileged(anyLong());

        mvc.perform(get(base("dependants"))).andExpect(status().isForbidden());

        verifyNoInteractions(dependants);
    }

    @Test
    @WithMockUser(roles = "HR_ADMIN")
    void dropdownsAreForHrAdmin() throws Exception {
        for (String path : new String[] {"relations", "languages", "language-levels", "education-levels"}) {
            mvc.perform(get("/api/v1/lookups/" + path)).andExpect(status().isOk());
        }
    }

    @Test
    @WithMockUser(roles = "HR_MANAGER")
    void dropdownsAreForbiddenToHrManager() throws Exception {
        for (String path : new String[] {"relations", "languages", "language-levels", "education-levels"}) {
            mvc.perform(get("/api/v1/lookups/" + path)).andExpect(status().isForbidden());
        }
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void dropdownsAreForbiddenToEmployees() throws Exception {
        mvc.perform(get("/api/v1/lookups/relations")).andExpect(status().isForbidden());
    }
}
