package com.hrms.workflow.service.impl;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.service.OrganisationLookup;
import com.hrms.workflow.dto.request.WorkflowActionRequest;
import com.hrms.workflow.entity.WorkflowDefinition;
import com.hrms.workflow.entity.WorkflowSetting;
import com.hrms.workflow.enums.RequestType;
import com.hrms.workflow.enums.StepOutcome;
import com.hrms.workflow.repository.RequestHistoryRepository;
import com.hrms.workflow.repository.WorkflowDefinitionRepository;
import com.hrms.workflow.repository.WorkflowRouteRepository;
import com.hrms.workflow.repository.WorkflowSettingRepository;
import com.hrms.workflow.repository.WorkflowStepRepository;
import com.hrms.workflow.service.RequestCompletionHandler;
import com.hrms.workflow.service.WorkflowDefinitionService;
import com.hrms.workflow.service.WorkflowRequestAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Guard branches of the workflow actions: each refusal happens before Flowable or the history is touched. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowServiceImplTest {

    static final long REQUEST = 42L;
    static final long REQUESTER = 1L;
    static final String PID = "pi-1";

    @Mock FlowEngine engine;
    @Mock StepEntry stepEntry;
    @Mock HistoryWriter history;
    @Mock WorkflowAuthorization authorization;
    @Mock OrganisationLookup organisation;
    @Mock EmployeeAccessGuard accessGuard;
    @Mock WorkflowDefinitionService definitionService;
    @Mock WorkflowDefinitionRepository definitions;
    @Mock WorkflowStepRepository steps;
    @Mock WorkflowRouteRepository routes;
    @Mock WorkflowSettingRepository settings;
    @Mock AuditTrail audit;
    @Mock ApproverResolver resolver;
    @Mock com.hrms.employee.service.DepartmentService departments;
    @Mock WorkflowRequestAccess access;
    @Mock RequestCompletionHandler handler;
    @Mock RequestHistoryRepository historyRows;

    WorkflowServiceImpl service;

    @BeforeEach
    void setUp() {
        when(access.type()).thenReturn(RequestType.LEAVE);
        when(handler.type()).thenReturn(RequestType.LEAVE);
        WorkflowPorts ports = new WorkflowPorts(List.of(access), List.of(handler), definitions);
        RequestScope scope = new RequestScope(ports, engine, definitions, steps, routes, historyRows, authorization,
            accessGuard);
        service = new WorkflowServiceImpl(ports, engine, stepEntry, history, authorization,
            new WorkflowInputs(organisation, settings), accessGuard, definitionService, scope, steps, routes, audit, resolver, departments);
        when(authorization.isOverseer()).thenReturn(true); // related; the participant rule has its own tests

        when(access.requesterEmployeeId(REQUEST)).thenReturn(REQUESTER);
        when(access.brief(REQUEST)).thenReturn(new WorkflowRequestAccess.Brief(REQUESTER, null, null));
        when(access.processInstanceId(REQUEST)).thenReturn(Optional.of(PID));
        when(access.targetDepartmentId(REQUEST)).thenReturn(null); // Mockito would answer 0L for a Long
        WorkflowDefinition def = new WorkflowDefinition();
        def.setDefinitionId(9L);
        def.setRequestType(RequestType.LEAVE);
        def.setFlowableProcessDefinitionId("pd-1");
        when(definitions.findByFlowableProcessDefinitionId("pd-1")).thenReturn(Optional.of(def));
        DefaultWorkflowFactory.Flow flow = DefaultWorkflowFactory.forType(RequestType.LEAVE);
        AtomicLong ids = new AtomicLong(100);
        flow.steps().forEach(s -> {
            s.setStepId(ids.incrementAndGet());
            s.setDefinitionId(9L);
        });
        when(steps.findByDefinitionIdOrderByPositionNo(9L)).thenReturn(flow.steps());
        when(routes.findByStepDefinitionId(9L)).thenReturn(flow.routes());
        when(organisation.departmentExists(5L)).thenReturn(true);
        when(organisation.isDepartmentActive(5L)).thenReturn(true);
        when(organisation.departmentExists(6L)).thenReturn(true);
    }

    private void enabled(String value) {
        WorkflowSetting s = new WorkflowSetting();
        s.setSettingKey("WORKFLOW_ENABLED_LEAVE");
        s.setSettingValue(value);
        when(settings.findById("WORKFLOW_ENABLED_LEAVE")).thenReturn(Optional.of(s));
    }

    private void liveTask(String key) {
        when(engine.openTask(PID)).thenReturn(Optional.of(new FlowEngine.OpenTask("t-1", key, "pd-1")));
    }

    private void assertNothingWritten() {
        verify(engine, never()).complete(anyString(), any());
        verify(engine, never()).start(anyString(), anyString(), any());
        verifyNoInteractions(history, stepEntry, audit);
        verify(handler, never()).onApproved(anyLong());
        verify(handler, never()).onRejected(anyLong(), any());
        verify(handler, never()).onCancelled(anyLong());
        verify(access, never()).markStage(anyLong(), any(), any(), any(), any(), any());
        verify(access, never()).markTerminal(anyLong(), any());
    }

    private String rule(Runnable r) {
        return assertThrows(BusinessRuleException.class, r::run).getRuleCode();
    }

    private void act(StepOutcome outcome, String step, Long target, String comment) {
        service.act(RequestType.LEAVE, REQUEST, new WorkflowActionRequest(step, outcome, target, comment));
    }

    // ----- submit -------------------------------------------------------------------------------------------------

    @Test
    void submitIsRefusedWhenTheFlagIsMissingOrFalse() {
        when(settings.findById("WORKFLOW_ENABLED_LEAVE")).thenReturn(Optional.empty());
        assertEquals("WORKFLOW_DISABLED", rule(() -> service.submit(RequestType.LEAVE, REQUEST, REQUESTER, null,
            Map.of("totalDays", 1))));
        enabled("false");
        assertEquals("WORKFLOW_DISABLED", rule(() -> service.submit(RequestType.LEAVE, REQUEST, REQUESTER, null,
            Map.of("totalDays", 1))));
        assertNothingWritten();
    }

    @Test
    void submitNeedsExactlyTheAllowListedFacts() {
        enabled("true");
        when(access.processInstanceId(REQUEST)).thenReturn(Optional.empty());
        when(definitionService.active(RequestType.LEAVE)).thenReturn(Optional.of(new WorkflowDefinition()));

        assertThrows(IllegalArgumentException.class,
            () -> service.submit(RequestType.LEAVE, REQUEST, REQUESTER, null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> service.submit(RequestType.LEAVE, REQUEST, REQUESTER, null,
            Map.of("totalDays", 1, "salary", 900)));
        assertNothingWritten();
    }

    @Test
    void submitForAnotherRequesterThanTheRequestsIsRefused() {
        enabled("true");
        when(access.processInstanceId(REQUEST)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
            () -> service.submit(RequestType.LEAVE, REQUEST, 99L, null, Map.of("totalDays", 1)));
        assertNothingWritten();
    }

    @Test
    void submitWithoutAPublishedFlowIsRefused() {
        enabled("true");
        when(access.processInstanceId(REQUEST)).thenReturn(Optional.empty());
        when(definitionService.active(RequestType.LEAVE)).thenReturn(Optional.empty());
        assertEquals("WORKFLOW_NOT_PUBLISHED",
            rule(() -> service.submit(RequestType.LEAVE, REQUEST, REQUESTER, null, Map.of("totalDays", 1))));
        assertNothingWritten();
    }

    // ----- act ----------------------------------------------------------------------------------------------------

    @Test
    void aStaleStepKeyIsAConflictEvenForTheFormerApprover() {
        liveTask("SECRETARY");
        assertThrows(OptimisticLockingFailureException.class, () -> act(StepOutcome.FORWARD, "BRANCH", 5L, null));
        verify(authorization, never()).assertMayAct(any(), anyLong(), any());
        assertNothingWritten();
    }

    @Test
    void authorizationIsCheckedOnTheLiveTasksStep() {
        liveTask("HEAD");
        when(stepEntry.enter(any())).thenReturn(new StepEntry.Ended("APPROVED"));
        act(StepOutcome.APPROVE, "HEAD", null, null);
        verify(authorization).assertMayAct(argThat(s -> s != null && "HEAD".equals(s.getStepKey())), eq(REQUESTER),
            isNull());
    }

    @Test
    void aClosedRequestIsAConflict() {
        when(engine.openTask(PID)).thenReturn(Optional.empty());
        assertThrows(OptimisticLockingFailureException.class, () -> act(StepOutcome.APPROVE, "HR", null, null));
        assertNothingWritten();
    }

    @Test
    void aLegacyRequestIsNotInTheWorkflow() {
        when(access.processInstanceId(REQUEST)).thenReturn(Optional.empty());
        assertEquals("NOT_IN_WORKFLOW", rule(() -> act(StepOutcome.APPROVE, "HR", null, null)));
        assertNothingWritten();
    }

    @Test
    void anUnauthorizedCallerIsRefusedBeforeAnythingElse() {
        liveTask("HEAD");
        doThrow(new AccessDeniedException("no")).when(authorization).assertMayAct(any(), anyLong(), any());
        assertThrows(AccessDeniedException.class, () -> act(StepOutcome.APPROVE, "HEAD", null, null));
        assertNothingWritten();
    }

    @Test
    void theSecretaryMayOnlyForward() {
        liveTask("SECRETARY");
        assertEquals("OUTCOME_NOT_ALLOWED", rule(() -> act(StepOutcome.APPROVE, "SECRETARY", null, null)));
        assertEquals("OUTCOME_NOT_ALLOWED", rule(() -> act(StepOutcome.REJECT, "SECRETARY", null, "x")));
        assertNothingWritten();
    }

    @Test
    void aRejectNeedsANonBlankCommentAndCommentsAreCapped() {
        liveTask("HEAD");
        assertEquals("COMMENT_REQUIRED", rule(() -> act(StepOutcome.REJECT, "HEAD", null, null)));
        assertEquals("COMMENT_REQUIRED", rule(() -> act(StepOutcome.REJECT, "HEAD", null, "   ")));
        assertEquals("TEXT_TOO_LONG", rule(() -> act(StepOutcome.REJECT, "HEAD", null, "x".repeat(501))));
        assertNothingWritten();
    }

    @Test
    void theBranchForwardNeedsAKnownActiveDepartment() {
        liveTask("BRANCH");
        assertEquals("TARGET_DEPARTMENT_REQUIRED", rule(() -> act(StepOutcome.FORWARD, "BRANCH", null, null)));
        assertEquals("DEPARTMENT_INACTIVE", rule(() -> act(StepOutcome.FORWARD, "BRANCH", 6L, null)));
        assertThrows(ResourceNotFoundException.class, () -> act(StepOutcome.FORWARD, "BRANCH", 7L, null));
        assertNothingWritten();
    }

    // ----- participant check (spec 10) ---------------------------------------------------------------------------

    @Test
    void anUnrelatedCallerGetsTheSame403ForUnknownLegacyClosedAndOtherStepRequests() {
        when(authorization.isOverseer()).thenReturn(false);
        when(accessGuard.currentEmployeeId()).thenReturn(77L);
        when(access.requesterEmployeeId(404L)).thenThrow(new ResourceNotFoundException("Request", "id", 404L));
        liveTask("HEAD");
        String atOtherStep = denied(() -> act(StepOutcome.APPROVE, "HEAD", null, null));
        when(engine.openTask(PID)).thenReturn(Optional.empty());
        String closed = denied(() -> act(StepOutcome.APPROVE, "HEAD", null, null));
        when(access.processInstanceId(REQUEST)).thenReturn(Optional.empty());
        String legacy = denied(() -> act(StepOutcome.APPROVE, "HEAD", null, null));
        String unknown = denied(() -> service.act(RequestType.LEAVE, 404L,
            new WorkflowActionRequest("HEAD", StepOutcome.APPROVE, null, null)));
        String history = denied(() -> service.history(RequestType.LEAVE, REQUEST));

        assertEquals(RequestScope.NO_ACCESS, atOtherStep);
        assertEquals(List.of(atOtherStep, atOtherStep, atOtherStep, atOtherStep), List.of(closed, legacy, unknown, history));
        verify(authorization, never()).assertMayAct(any(), anyLong(), any());
        assertNothingWritten();
    }

    @Test
    void anOverseerLearnsThatAnIdIsUnknown() {
        when(access.requesterEmployeeId(404L)).thenThrow(new ResourceNotFoundException("Request", "id", 404L));
        assertThrows(ResourceNotFoundException.class, () -> service.history(RequestType.LEAVE, 404L));
    }

    @Test
    void aPastActorAndAStepHolderAreRelated() {
        when(authorization.isOverseer()).thenReturn(false);
        when(accessGuard.currentEmployeeId()).thenReturn(77L);
        liveTask("HEAD");
        when(historyRows.existsByRequestTypeAndRequestIdAndActorEmployeeId(RequestType.LEAVE, REQUEST, 77L))
            .thenReturn(true);
        assertDoesNotThrow(() -> service.history(RequestType.LEAVE, REQUEST));

        when(historyRows.existsByRequestTypeAndRequestIdAndActorEmployeeId(RequestType.LEAVE, REQUEST, 77L))
            .thenReturn(false);
        when(authorization.holdsAny(any(), eq(REQUESTER), isNull())).thenReturn(true);
        assertDoesNotThrow(() -> service.history(RequestType.LEAVE, REQUEST));
    }

    private static String denied(Runnable r) {
        return assertThrows(AccessDeniedException.class, r::run).getMessage();
    }

    @Test
    void aDoubleFactBecomesTheExactDecimal() {
        assertEquals(new java.math.BigDecimal("0.1"),
            WorkflowInputs.variables(RequestType.LEAVE, Map.of("totalDays", 0.1d)).get("totalDays"));
        assertEquals(new java.math.BigDecimal("12"),
            WorkflowInputs.variables(RequestType.LEAVE, Map.of("totalDays", 12)).get("totalDays"));
    }

    @Test
    void aFlagWithANullValueCountsAsOff() {
        enabled(null);
        assertEquals("WORKFLOW_DISABLED", rule(() -> service.submit(RequestType.LEAVE, REQUEST, REQUESTER, null,
            Map.of("totalDays", 1))));
    }

    // ----- cancel -------------------------------------------------------------------------------------------------

    @Test
    void onlyTheRequesterMayCancel() {
        liveTask("HEAD");
        when(accessGuard.currentEmployeeId()).thenReturn(2L);
        assertThrows(AccessDeniedException.class, () -> service.cancel(RequestType.LEAVE, REQUEST));
        when(accessGuard.currentEmployeeId()).thenReturn(null);
        assertThrows(AccessDeniedException.class, () -> service.cancel(RequestType.LEAVE, REQUEST));
        verify(engine, never()).cancel(anyString());
        assertNothingWritten();
    }

    // ----- ports --------------------------------------------------------------------------------------------------

    @Test
    void aTypeWithoutPortIsNotSupported() {
        assertEquals("WORKFLOW_NOT_SUPPORTED", rule(() -> service.cancel(RequestType.OVERTIME, REQUEST)));
    }

    @Test
    void startupFailsWhenAnActiveTypeHasNoPortButNotForUnusedTypes() {
        WorkflowPorts none = new WorkflowPorts(List.of(), List.of(), definitions);
        when(definitions.findByRequestTypeAndStatus(any(), eq(WorkflowDefinition.STATUS_ACTIVE)))
            .thenReturn(Optional.empty());
        assertDoesNotThrow(none::verify);

        when(definitions.findByRequestTypeAndStatus(RequestType.OVERTIME, WorkflowDefinition.STATUS_ACTIVE))
            .thenReturn(Optional.of(new WorkflowDefinition()));
        IllegalStateException e = assertThrows(IllegalStateException.class, none::verify);
        assertTrue(e.getMessage().contains("OVERTIME"), e.getMessage());
    }

    @Test
    void twoPortsForOneTypeFailAtStartup() {
        assertThrows(IllegalStateException.class,
            () -> new WorkflowPorts(List.of(access, access), List.of(handler), definitions));
    }
}
