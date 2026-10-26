package com.hrms.workflow.controller;

import com.hrms.common.dto.PagedResponse;
import com.hrms.common.exception.GlobalExceptionHandler;
import com.hrms.workflow.dto.request.WorkflowActionRequest;
import com.hrms.workflow.dto.request.WorkflowOverrideRequest;
import com.hrms.workflow.dto.response.InboxItemResponse;
import com.hrms.workflow.dto.response.RequestHistoryResponse;
import com.hrms.workflow.dto.response.WorkflowStatusResponse;
import com.hrms.workflow.entity.WorkflowDefinition;
import com.hrms.workflow.enums.HistoryAction;
import com.hrms.workflow.enums.RequestType;
import com.hrms.workflow.enums.StepOutcome;
import com.hrms.workflow.service.InboxService;
import com.hrms.workflow.service.WorkflowDefinitionService;
import com.hrms.workflow.service.WorkflowService;
import com.hrms.workflow.service.impl.WorkflowAdminPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role gate of every workflow endpoint (the coarse {@code @PreAuthorize}); the participant / step checks are the
 * service's and are covered by the Task 8 tests. A denied call must never reach the service.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = WorkflowControllerTest.TestConfig.class)
class WorkflowControllerTest {

    private static final String BASE = "/api/v1/requests/LEAVE/7";
    private static final String PUBLISH = "/api/v1/admin/workflows/LEAVE/publish-default";
    private static final String ACT = "{\"stepKey\":\"BRANCH\",\"outcome\":\"FORWARD\",\"targetDepartmentId\":3}";
    private static final String OVERRIDE = "{\"stepKey\":\"BRANCH\",\"outcome\":\"FORWARD\",\"targetDepartmentId\":3,"
        + "\"reason\":\"manager away\"}";

    // Not annotated @Configuration on purpose, see EmployeeEndpointSecurityTest.
    @EnableWebMvc
    @EnableMethodSecurity
    static class TestConfig {
        @Bean GlobalExceptionHandler globalExceptionHandler() { return new GlobalExceptionHandler(); }
        @Bean WorkflowService workflowService() { return mock(WorkflowService.class); }
        @Bean WorkflowDefinitionService definitionService() { return mock(WorkflowDefinitionService.class); }
        @Bean InboxService inboxService() { return mock(InboxService.class); }
        @Bean("workflowAdminPolicy") WorkflowAdminPolicy workflowAdminPolicy() { return mock(WorkflowAdminPolicy.class); }
        @Bean WorkflowController workflowController(WorkflowService w, WorkflowDefinitionService d) {
            return new WorkflowController(w, d);
        }
        @Bean InboxController inboxController(InboxService i) { return new InboxController(i); }
    }

    @Autowired WebApplicationContext context;
    @Autowired WorkflowService workflow;
    @Autowired WorkflowDefinitionService definitions;
    @Autowired InboxService inbox;
    @Autowired WorkflowAdminPolicy policy;
    MockMvc mvc;

    private final WorkflowStatusResponse pending = new WorkflowStatusResponse(RequestType.LEAVE, 7, "PENDING_BRANCH",
        "BRANCH", "Branch manager", "Branch manager AR", List.of(StepOutcome.FORWARD, StepOutcome.REJECT), true, 3L, "Req Name", "ANNUAL 2027-03-01 to 2027-03-03 (3.0 days)");

    @BeforeEach
    void setUp() {
        reset(workflow, definitions, inbox, policy);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.contentType("application/json").content(body);
    }

    private static PagedResponse<InboxItemResponse> emptyPage() {
        return PagedResponse.from(new PageImpl<InboxItemResponse>(List.of()));
    }

    /** Everything a signed-in participant role may call, except override and publish. */
    private void assertParticipantEndpointsAllowed() throws Exception {
        when(workflow.status(RequestType.LEAVE, 7)).thenReturn(pending);
        when(workflow.act(eq(RequestType.LEAVE), eq(7L), any(WorkflowActionRequest.class))).thenReturn(pending);
        when(workflow.cancel(RequestType.LEAVE, 7)).thenReturn(pending);
        when(workflow.history(RequestType.LEAVE, 7)).thenReturn(List.of(new RequestHistoryResponse("BRANCH",
            HistoryAction.SUBMITTED, 1L, null, "EMPLOYEE", null, LocalDateTime.of(2026, 10, 1, 9, 0))));
        when(inbox.inbox(0, 10)).thenReturn(emptyPage());

        mvc.perform(get(BASE + "/workflow")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stage").value("PENDING_BRANCH"))
            .andExpect(jsonPath("$.data.requesterName").value("Req Name"));
        when(workflow.targetDepartments(RequestType.LEAVE, 7)).thenReturn(List.of());
        mvc.perform(get(BASE + "/workflow/target-departments")).andExpect(status().isOk());
        mvc.perform(json(post(BASE + "/actions"), ACT)).andExpect(status().isOk());
        mvc.perform(post(BASE + "/cancel")).andExpect(status().isOk());
        mvc.perform(get(BASE + "/history")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].action").value("SUBMITTED"));
        mvc.perform(get("/api/v1/inbox")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isEmpty());
        verify(workflow).act(eq(RequestType.LEAVE), eq(7L), any(WorkflowActionRequest.class));
    }

    private void assertOverrideAndPublishForbidden() throws Exception {
        mvc.perform(json(post(BASE + "/override"), OVERRIDE)).andExpect(status().isForbidden());
        mvc.perform(post(PUBLISH)).andExpect(status().isForbidden());
        verifyNoInteractions(definitions);
    }

    @Test @WithMockUser(roles = "EMPLOYEE")
    void employee_mayCallParticipantEndpoints_butNotOverrideOrPublish() throws Exception {
        assertParticipantEndpointsAllowed();
        assertOverrideAndPublishForbidden();
        org.mockito.Mockito.verify(workflow, org.mockito.Mockito.never()).override(any(), anyLong(), any());
    }

    @Test @WithMockUser(roles = "HR_MANAGER")
    void hrManager_mayCallParticipantEndpoints_butNotOverrideOrPublish() throws Exception {
        assertParticipantEndpointsAllowed();
        assertOverrideAndPublishForbidden();
    }

    @Test @WithMockUser(roles = "GENERAL_MANAGER")
    void generalManager_mayCallParticipantEndpoints_butNotOverrideOrPublish() throws Exception {
        assertParticipantEndpointsAllowed();
        assertOverrideAndPublishForbidden();
    }

    @Test @WithMockUser(roles = "HR_ADMIN")
    void hrAdmin_mayDoEverything() throws Exception {
        assertParticipantEndpointsAllowed();
        when(workflow.override(eq(RequestType.LEAVE), eq(7L), any(WorkflowOverrideRequest.class))).thenReturn(pending);
        mvc.perform(json(post(BASE + "/override"), OVERRIDE)).andExpect(status().isOk());
        when(definitions.publishDefault(RequestType.LEAVE)).thenReturn(new WorkflowDefinition());
        mvc.perform(post(PUBLISH)).andExpect(status().isOk());
        verify(definitions).publishDefault(RequestType.LEAVE);
    }

    @Test @WithMockUser(roles = "SYSTEM_ADMIN")
    void systemAdmin_hasNoParticipantAccess() throws Exception {
        mvc.perform(get(BASE + "/workflow")).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/workflow/target-departments")).andExpect(status().isForbidden());
        mvc.perform(json(post(BASE + "/actions"), ACT)).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/cancel")).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/history")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/inbox")).andExpect(status().isForbidden());
        mvc.perform(json(post(BASE + "/override"), OVERRIDE)).andExpect(status().isForbidden());
        verifyNoInteractions(workflow, inbox);
    }

    @Test @WithMockUser(roles = "SYSTEM_ADMIN")
    void systemAdmin_mayPublishOnlyWhenTheSettingIsOn() throws Exception {
        when(policy.systemAdminMayManage()).thenReturn(false);
        mvc.perform(post(PUBLISH)).andExpect(status().isForbidden());
        verifyNoInteractions(definitions);

        when(policy.systemAdminMayManage()).thenReturn(true);
        when(definitions.publishDefault(RequestType.LEAVE)).thenReturn(new WorkflowDefinition());
        mvc.perform(post(PUBLISH)).andExpect(status().isOk());
        verify(definitions).publishDefault(RequestType.LEAVE);
    }

    @Test @WithMockUser(roles = "EMPLOYEE")
    void employee_cannotPublishEvenIfTheSystemAdminSettingIsOn() throws Exception {
        when(policy.systemAdminMayManage()).thenReturn(true);
        mvc.perform(post(PUBLISH)).andExpect(status().isForbidden());
        verifyNoInteractions(definitions);
    }

    @Test @WithMockUser(roles = "EMPLOYEE")
    void invalidBodyOrUnknownType_isA400() throws Exception {
        mvc.perform(json(post(BASE + "/actions"), "{\"outcome\":\"FORWARD\"}")).andExpect(status().isBadRequest());
        mvc.perform(json(post(BASE + "/actions"), "{\"stepKey\":\"BRANCH\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/requests/NOPE/7/workflow")).andExpect(status().isBadRequest());
        verifyNoInteractions(workflow);
    }

    @Test @WithMockUser(roles = "HR_ADMIN")
    void overrideNeedsAReason() throws Exception {
        mvc.perform(json(post(BASE + "/override"),
            "{\"stepKey\":\"BRANCH\",\"outcome\":\"FORWARD\",\"targetDepartmentId\":3}")).andExpect(status().isBadRequest());
        verifyNoInteractions(workflow);
    }

    @Test @WithMockUser(roles = "EMPLOYEE")
    void inboxPassesPageAndSize() throws Exception {
        when(inbox.inbox(2, 25)).thenReturn(emptyPage());
        mvc.perform(get("/api/v1/inbox?page=2&size=25")).andExpect(status().isOk());
        verify(inbox).inbox(2, 25);
    }
}
