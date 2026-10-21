package com.hrms.employee.controller;

import com.hrms.common.exception.GlobalExceptionHandler;
import com.hrms.recruitment.controller.RecruitmentLookupController;
import com.hrms.recruitment.controller.RejectionReasonAdminController;
import com.hrms.recruitment.controller.RequisitionController;
import com.hrms.recruitment.controller.StageAdminController;
import com.hrms.recruitment.service.RejectionReasonService;
import com.hrms.recruitment.service.RequisitionService;
import com.hrms.recruitment.service.StageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may call the recruitment endpoints (the JWT filter chain is not part of this test, same setup as
 * MasterAdminControllersSecurityTest). Requisitions: HR and EMPLOYEE may call the raise/read/cancel verbs, only HR may
 * decide; SYSTEM_ADMIN has no access. Stages and rejection reasons: SYSTEM_ADMIN only. Lookups: HR only.
 * Whether an EMPLOYEE may act on a particular requisition is a data-level rule, tested in RequisitionServiceImplTest.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = RecruitmentControllersSecurityTest.TestConfig.class)
class RecruitmentControllersSecurityTest {

    // Not annotated @Configuration on purpose, see EmployeeEndpointSecurityTest.
    @EnableWebMvc
    @EnableMethodSecurity
    static class TestConfig {
        @Bean GlobalExceptionHandler globalExceptionHandler() { return new GlobalExceptionHandler(); }
        // Spring Boot registers this in the real application; it is what makes @Validated query parameters count.
        @Bean static MethodValidationPostProcessor methodValidation() { return new MethodValidationPostProcessor(); }
        @Bean RequisitionController requisitions() { return new RequisitionController(mock(RequisitionService.class)); }
        @Bean StageAdminController stages() { return new StageAdminController(mock(StageService.class)); }
        @Bean RejectionReasonAdminController reasons() { return new RejectionReasonAdminController(mock(RejectionReasonService.class)); }
        @Bean RecruitmentLookupController lookups() {
            return new RecruitmentLookupController(mock(StageService.class), mock(RejectionReasonService.class));
        }
    }

    private static final String REQ = "/api/v1/recruitment/requisitions";
    private static final String JSON = "application/json";
    private static final String REQUISITION_BODY = """
        {"jobTitle":"Analyst","departmentId":5,"designationId":7,"noOfPositions":1,"employmentType":"FULL_TIME",
         "requisitionType":"NEW","minExperienceYears":0,"priority":"LOW","version":0}""";
    private static final String ACTION_BODY = "{\"version\":0}";
    private static final String APPROVE_BODY = "{\"budgetConfirmed\":true,\"managementConfirmed\":true,\"version\":0}";
    private static final String REJECT_BODY = "{\"reason\":\"No budget\",\"version\":0}";
    private static final String STAGE_BODY = "{\"code\":\"A1\",\"name\":\"n\",\"nameAr\":\"n\",\"stageType\":\"OPEN\",\"sortOrder\":1,\"version\":0}";
    private static final String REASON_BODY =
        "{\"code\":\"A1\",\"name\":\"n\",\"nameAr\":\"n\",\"appliesTo\":\"APPLICATION\",\"sortOrder\":1,\"version\":0}";

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** Signs in with one role, for the parameterised tests (@WithMockUser cannot take a parameter). */
    private static void as(String role) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("u", "n/a", AuthorityUtils.createAuthorityList("ROLE_" + role)));
    }

    /** Raise, read, edit, submit and cancel: open to HR and to any employee (the service narrows it per record). */
    private void requesterVerbs(int expected) throws Exception {
        int created = expected == 200 ? 201 : expected;
        mvc.perform(get(REQ)).andExpect(status().is(expected));
        mvc.perform(get(REQ + "/1")).andExpect(status().is(expected));
        mvc.perform(get(REQ + "/raise-options")).andExpect(status().is(expected));
        mvc.perform(get(REQ + "/raise-options/people?departmentId=5")).andExpect(status().is(expected));
        mvc.perform(post(REQ).contentType(JSON).content(REQUISITION_BODY)).andExpect(status().is(created));
        mvc.perform(put(REQ + "/1").contentType(JSON).content(REQUISITION_BODY)).andExpect(status().is(expected));
        mvc.perform(post(REQ + "/1/submit").contentType(JSON).content(ACTION_BODY)).andExpect(status().is(expected));
        mvc.perform(post(REQ + "/1/cancel").contentType(JSON).content(ACTION_BODY)).andExpect(status().is(expected));
    }

    /** Approve, reject, hold, resume, close: HR only. */
    private void decisionVerbs(int expected) throws Exception {
        mvc.perform(post(REQ + "/1/approve").contentType(JSON).content(APPROVE_BODY)).andExpect(status().is(expected));
        mvc.perform(post(REQ + "/1/reject").contentType(JSON).content(REJECT_BODY)).andExpect(status().is(expected));
        mvc.perform(post(REQ + "/1/hold").contentType(JSON).content(ACTION_BODY)).andExpect(status().is(expected));
        mvc.perform(post(REQ + "/1/resume").contentType(JSON).content(ACTION_BODY)).andExpect(status().is(expected));
        mvc.perform(post(REQ + "/1/close").contentType(JSON).content(ACTION_BODY)).andExpect(status().is(expected));
    }

    private void adminVerbs(String path, String body, int expected) throws Exception {
        String base = "/api/v1/admin/recruitment/" + path;
        int created = expected == 200 ? 201 : expected;
        mvc.perform(get(base)).andExpect(status().is(expected));
        mvc.perform(get(base + "/1")).andExpect(status().is(expected));
        mvc.perform(post(base).contentType(JSON).content(body)).andExpect(status().is(created));
        mvc.perform(put(base + "/1").contentType(JSON).content(body)).andExpect(status().is(expected));
        mvc.perform(patch(base + "/1/deactivate")).andExpect(status().is(expected));
        mvc.perform(patch(base + "/1/activate")).andExpect(status().is(expected));
        mvc.perform(delete(base + "/1")).andExpect(status().is(expected));
    }

    // ------------------------------------------------------------------------------ requisitions

    @ParameterizedTest
    @ValueSource(strings = {"HR_ADMIN", "HR_MANAGER"})
    void hrCanDoEverythingOnRequisitions(String role) throws Exception {
        as(role);
        requesterVerbs(200);
        decisionVerbs(200);
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void anEmployeeMayRaiseAndReadButNeverDecide() throws Exception {
        requesterVerbs(200);
        decisionVerbs(403);
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void systemAdminHasNoAccessToRequisitions() throws Exception {
        requesterVerbs(403);
        decisionVerbs(403);
    }

    @Test
    @WithMockUser(roles = "HR_MANAGER")
    void requestsMissingTheirVersionOrConfirmationsAreRejectedBeforeTheService() throws Exception {
        mvc.perform(post(REQ + "/1/hold").contentType(JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post(REQ + "/1/approve").contentType(JSON)
            .content("{\"budgetConfirmed\":true,\"managementConfirmed\":false,\"version\":0}")).andExpect(status().isBadRequest());
        mvc.perform(post(REQ + "/1/reject").contentType(JSON).content("{\"reason\":\"  \",\"version\":0}")).andExpect(status().isBadRequest());
        mvc.perform(get(REQ + "?size=1000")).andExpect(status().isBadRequest());
    }

    // ----------------------------------------------------------------------------------- masters

    @ParameterizedTest
    @ValueSource(strings = {"HR_ADMIN", "HR_MANAGER", "EMPLOYEE"})
    void onlySystemAdminMaintainsStagesAndReasons(String role) throws Exception {
        as(role);
        adminVerbs("stages", STAGE_BODY, 403);
        adminVerbs("rejection-reasons", REASON_BODY, 403);
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void systemAdminMaintainsStagesAndReasons() throws Exception {
        adminVerbs("stages", STAGE_BODY, 200);
        adminVerbs("rejection-reasons", REASON_BODY, 200);
    }

    // ------------------------------------------------------------------------------------ lookups

    @ParameterizedTest
    @ValueSource(strings = {"EMPLOYEE", "SYSTEM_ADMIN"})
    void lookupsAreForHrOnly(String role) throws Exception {
        as(role);
        mvc.perform(get("/api/v1/recruitment/lookups/stages")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/recruitment/lookups/rejection-reasons")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR_MANAGER")
    void hrReadsTheLookups() throws Exception {
        mvc.perform(get("/api/v1/recruitment/lookups/stages")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/recruitment/lookups/rejection-reasons?appliesTo=OFFER")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/recruitment/lookups/rejection-reasons?appliesTo=NONSENSE")).andExpect(status().isBadRequest());
    }
}
