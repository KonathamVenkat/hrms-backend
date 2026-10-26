package com.hrms.workflow.service.impl;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.DatabaseTest;
import com.hrms.employee.EmployeeApplication;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.service.OrganisationLookup;
import com.hrms.workflow.dto.request.WorkflowActionRequest;
import com.hrms.workflow.dto.request.WorkflowOverrideRequest;
import com.hrms.workflow.dto.response.RequestHistoryResponse;
import com.hrms.workflow.dto.response.WorkflowStatusResponse;
import com.hrms.workflow.entity.WorkflowDefinition;
import com.hrms.workflow.entity.WorkflowSetting;
import com.hrms.workflow.enums.ApproverType;
import com.hrms.workflow.enums.HistoryAction;
import com.hrms.workflow.enums.RequestType;
import com.hrms.workflow.enums.StepOutcome;
import com.hrms.workflow.repository.WorkflowSettingRepository;
import com.hrms.workflow.service.RequestCompletionHandler;
import com.hrms.workflow.service.WorkflowDefinitionService;
import com.hrms.workflow.service.WorkflowRequestAccess;
import com.hrms.workflow.service.WorkflowService;
import jakarta.persistence.EntityManager;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * The v1 leave route end to end on real Oracle + Flowable. The request table is an in-memory fake port (leave is
 * Task 8); the organisation and the caller's employee id are stubbed, roles come from a real SecurityContext.
 */
@SpringBootTest(classes = EmployeeApplication.class, properties = "spring.main.allow-bean-definition-overriding=true")
@Import(WorkflowActionsDbTest.Fakes.class)
@Transactional
class WorkflowActionsDbTest extends DatabaseTest {

    static final long DEPT = 501L;
    static final long INACTIVE_DEPT = 502L;
    static final long UNKNOWN_DEPT = 503L;
    private static final AtomicLong REQUEST_IDS = new AtomicLong(7_000_000L);

    @Autowired WorkflowService workflow;
    @Autowired WorkflowDefinitionService definitionService;
    @Autowired WorkflowDefinitionServiceImpl definitionImpl;
    @Autowired WorkflowSettingRepository settings;
    @Autowired EmployeeRepository employees;
    @Autowired com.hrms.employee.repository.DepartmentRepository departmentRepository;
    @Autowired FakeRequestAccess requests;
    @Autowired FakeCompletionHandler handler;
    @Autowired RuntimeService runtimeService;
    @Autowired TaskService taskService;
    @Autowired RepositoryService repositoryService;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired EntityManager em;
    @Autowired org.springframework.context.ApplicationEventPublisher events;
    @MockitoBean OrganisationLookup organisation;
    @MockitoBean EmployeeAccessGuard guard;

    long requester, branchManager, secretary, head, gm, hr1, hr2, other;
    private final ThreadLocal<Long> caller = new ThreadLocal<>();

    @BeforeEach
    void setUp() {
        requests.rows.clear();
        handler.calls.clear();
        handler.failOnApproved = false;
        when(guard.currentEmployeeId()).thenAnswer(i -> caller.get());
        when(guard.isHrAdmin()).thenAnswer(i -> SecurityContextHolder.getContext().getAuthentication() != null
            && SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_HR_ADMIN")));
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            seedEmployees();
            definitionService.publishDefault(RequestType.LEAVE);
            enable(true);
        }
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        caller.remove();
    }

    private void seedEmployees() {
        String run = Long.toString(System.nanoTime(), 36);
        requester = employee("req", run);
        branchManager = employee("bm", run);
        secretary = employee("sec", run);
        head = employee("head", run);
        gm = employee("gm", run);
        hr1 = employee("hr1", run);
        hr2 = employee("hr2", run);
        other = employee("oth", run);
        organise();
    }

    /** One branch (manager BM) and one active department (secretary SEC, head HEAD). */
    private void organise() {
        for (long e : List.of(requester, secretary, head, gm, hr1, hr2, other, branchManager)) {
            when(organisation.branchManagerOf(e)).thenReturn(Optional.of(branchManager));
        }
        when(organisation.secretaryOf(DEPT)).thenReturn(Optional.of(secretary));
        when(organisation.headOf(DEPT)).thenReturn(Optional.of(head));
        when(organisation.departmentExists(DEPT)).thenReturn(true);
        when(organisation.isDepartmentActive(DEPT)).thenReturn(true);
        when(organisation.departmentExists(INACTIVE_DEPT)).thenReturn(true);
        when(organisation.isDepartmentActive(INACTIVE_DEPT)).thenReturn(false);
    }

    private long employee(String tag, String run) {
        return employees.saveAndFlush(Employee.builder()
            .firstName("Wf").firstNameAr("ع").lastName(tag).lastNameAr("ل")
            .dateOfBirth(LocalDate.of(1990, 1, 1)).gender(Gender.MALE)
            .personalEmail("wf-" + tag + "-" + run + "-p@example.test")
            .workEmail("wf-" + tag + "-" + run + "-w@example.test")
            .hireDate(LocalDate.of(2024, 1, 1)).employmentType(EmploymentType.FULL_TIME).build()).getId();
    }

    private void enable(boolean on) {
        WorkflowSetting flag = settings.findById("WORKFLOW_ENABLED_LEAVE").orElseThrow();
        flag.setSettingValue(Boolean.toString(on));
        settings.saveAndFlush(flag);
    }

    private void as(long employeeId, String role) {
        caller.set(employeeId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "user" + employeeId, "n/a", AuthorityUtils.createAuthorityList("ROLE_" + role)));
    }

    // ----- helpers --------------------------------------------------------------------------------------------

    private long newRequest(long by) {
        long id = REQUEST_IDS.incrementAndGet();
        requests.rows.put(id, new FakeRequestAccess.Row(by));
        return id;
    }

    private WorkflowStatusResponse submit(long id, long by, Long target) {
        as(by, "EMPLOYEE");
        return workflow.submit(RequestType.LEAVE, id, by, target, Map.of("totalDays", 5));
    }

    private WorkflowStatusResponse act(long id, long actor, String role, String step, StepOutcome outcome,
                                       Long target, String comment) {
        as(actor, role);
        return workflow.act(RequestType.LEAVE, id, new WorkflowActionRequest(step, outcome, target, comment));
    }

    /** Employee request forwarded by the branch manager to DEPT, then by the secretary: now at HEAD. */
    private long atHead() {
        long id = newRequest(requester);
        submit(id, requester, null);
        act(id, branchManager, "EMPLOYEE", "BRANCH", StepOutcome.FORWARD, DEPT, null);
        act(id, secretary, "EMPLOYEE", "SECRETARY", StepOutcome.FORWARD, null, null);
        return id;
    }

    private List<HistoryAction> actions(long id) {
        return historyOf(id).stream().map(RequestHistoryResponse::action).toList();
    }

    /** Reads the history as an HR manager (always related) and restores the caller. */
    private List<RequestHistoryResponse> historyOf(long id) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Long before = caller.get();
        try {
            as(hr1, "HR_MANAGER");
            return workflow.history(RequestType.LEAVE, id);
        } finally {
            SecurityContextHolder.getContext().setAuthentication(auth);
            caller.set(before);
        }
    }

    private String ruleOf(Runnable r) {
        return assertThrows(BusinessRuleException.class, r::run).getRuleCode();
    }

    // ----- submit ----------------------------------------------------------------------------------------------

    @Test
    void submitWithTheFlagOffIsWorkflowDisabled() {
        enable(false);
        long id = newRequest(requester);
        assertEquals("WORKFLOW_DISABLED", ruleOf(() -> submit(id, requester, null)));
        assertTrue(requests.rows.get(id).processInstanceId == null);
    }

    @Test
    void anEmployeeSubmitWaitsForTheBranchManager() {
        long id = newRequest(requester);
        WorkflowStatusResponse s = submit(id, requester, null);

        assertEquals("PENDING_BRANCH", s.stage());
        assertEquals("BRANCH", s.stepKey());
        // the caller is the requester: the step is shown, but nothing is offered to act on it
        assertFalse(s.pickTargetDepartment());
        assertEquals(List.of(), s.allowedOutcomes());
        assertEquals(requester, s.requesterEmployeeId());
        assertEquals("Fake requester", s.requesterName());
        FakeRequestAccess.Row row = requests.rows.get(id);
        assertEquals("PENDING_BRANCH", row.stage);
        assertEquals("BRANCH_MANAGER", row.approverType);
        assertEquals(branchManager, row.approverEmployeeId);
        assertNotNull(row.definitionId);
        assertNotNull(row.processInstanceId);
        List<RequestHistoryResponse> h = historyOf(id);
        assertEquals(1, h.size());
        assertEquals(HistoryAction.SUBMITTED, h.get(0).action());
        assertEquals(requester, h.get(0).actorEmployeeId());
        assertEquals("EMPLOYEE", h.get(0).actorRole());
    }

    @Test
    void submittingTwiceIsAConflict() {
        long id = newRequest(requester);
        submit(id, requester, null);
        assertThrows(OptimisticLockingFailureException.class, () -> submit(id, requester, null));
    }

    // ----- branch step ----------------------------------------------------------------------------------------

    @Test
    void branchForwardNeedsAnActiveKnownDepartment() {
        long id = newRequest(requester);
        submit(id, requester, null);

        assertEquals("TARGET_DEPARTMENT_REQUIRED",
            ruleOf(() -> act(id, branchManager, "EMPLOYEE", "BRANCH", StepOutcome.FORWARD, null, null)));
        assertEquals("DEPARTMENT_INACTIVE",
            ruleOf(() -> act(id, branchManager, "EMPLOYEE", "BRANCH", StepOutcome.FORWARD, INACTIVE_DEPT, null)));
        assertThrows(ResourceNotFoundException.class,
            () -> act(id, branchManager, "EMPLOYEE", "BRANCH", StepOutcome.FORWARD, UNKNOWN_DEPT, null));
        assertEquals(List.of(HistoryAction.SUBMITTED), actions(id));
    }

    @Test
    void theWholeRouteEndsApprovedOnlyWhenHrProcesses() {
        long id = newRequest(requester);
        submit(id, requester, null);

        WorkflowStatusResponse s = act(id, branchManager, "EMPLOYEE", "BRANCH", StepOutcome.FORWARD, DEPT, null);
        assertEquals("PENDING_SECRETARY", s.stage());
        assertEquals(List.of(), s.allowedOutcomes()); // the branch manager cannot act on the secretary step
        assertEquals(DEPT, requests.rows.get(id).targetDepartmentId);
        assertEquals(secretary, requests.rows.get(id).approverEmployeeId);

        assertEquals("OUTCOME_NOT_ALLOWED",
            ruleOf(() -> act(id, secretary, "EMPLOYEE", "SECRETARY", StepOutcome.APPROVE, null, null)));
        assertEquals("OUTCOME_NOT_ALLOWED",
            ruleOf(() -> act(id, secretary, "EMPLOYEE", "SECRETARY", StepOutcome.REJECT, null, "no")));

        assertEquals("PENDING_HEAD", act(id, secretary, "EMPLOYEE", "SECRETARY", StepOutcome.FORWARD, null, null).stage());
        assertEquals(head, requests.rows.get(id).approverEmployeeId);
        assertEquals("PENDING_GM", act(id, head, "EMPLOYEE", "HEAD", StepOutcome.APPROVE, null, null).stage());
        assertEquals("GENERAL_MANAGER", requests.rows.get(id).approverRole);
        assertEquals("ROLE", requests.rows.get(id).approverType);
        assertNull(requests.rows.get(id).approverEmployeeId);
        assertEquals("PENDING_HR", act(id, gm, "GENERAL_MANAGER", "GM", StepOutcome.APPROVE, null, null).stage());
        assertEquals(List.of(), handler.calls);

        WorkflowStatusResponse done = act(id, hr1, "HR_ADMIN", "HR", StepOutcome.APPROVE, null, "processed");
        assertEquals("APPROVED", done.stage());
        assertNull(done.stepKey());
        assertEquals(List.of(), done.allowedOutcomes());
        assertEquals(List.of("approved:" + id), handler.calls);
        assertEquals("APPROVED", requests.rows.get(id).stage);
        assertNull(requests.rows.get(id).stepKey);
        assertEquals(List.of(HistoryAction.SUBMITTED, HistoryAction.FORWARDED, HistoryAction.FORWARDED,
            HistoryAction.APPROVED, HistoryAction.APPROVED, HistoryAction.APPROVED), actions(id));
        assertEquals(List.of("BRANCH", "SECRETARY", "HEAD", "GM", "HR"), historyOf(id)
            .stream().skip(1).map(RequestHistoryResponse::stepKey).toList());
        assertEquals(0, runtimeService.createProcessInstanceQuery().processInstanceId(
            requests.rows.get(id).processInstanceId).count());
        assertEquals(3, jdbc.queryForObject("select count(*) from AUDIT_EVENTS where TARGET_TYPE = 'LEAVE'"
            + " and TARGET_ID = ? and ACTION = 'WORKFLOW_APPROVED'", Long.class, String.valueOf(id)));
    }

    @Test
    void aDepartmentWithoutSecretarySkipsTheSecretaryStep() {
        when(organisation.secretaryOf(DEPT)).thenReturn(Optional.empty());
        long id = newRequest(requester);
        submit(id, requester, null);

        assertEquals("PENDING_HEAD", act(id, branchManager, "EMPLOYEE", "BRANCH", StepOutcome.FORWARD, DEPT, null).stage());
        RequestHistoryResponse skip = historyOf(id).get(2);
        assertEquals(HistoryAction.AUTO_ROUTED, skip.action());
        assertEquals("SECRETARY", skip.stepKey());
        assertNull(skip.actorEmployeeId());
        assertEquals("SKIP_NO_APPROVER", reasonCode(id, 2));
    }

    private String reasonCode(long id, int index) {
        em.flush(); // history rows are JPA inserts; this query is plain JDBC
        return jdbc.queryForList("select REASON_CODE from REQUEST_HISTORY where REQUEST_TYPE = 'LEAVE' and REQUEST_ID = ?"
            + " order by ACTED_AT, HISTORY_ID", String.class, id).get(index);
    }

    // ----- head / reject ---------------------------------------------------------------------------------------

    @Test
    void headRejectNeedsACommentAndEndsTheFlowRejected() {
        long id = atHead();

        assertEquals("COMMENT_REQUIRED", ruleOf(() -> act(id, head, "EMPLOYEE", "HEAD", StepOutcome.REJECT, null, "  ")));
        WorkflowStatusResponse s = act(id, head, "EMPLOYEE", "HEAD", StepOutcome.REJECT, null, " Busy season ");

        assertEquals("REJECTED", s.stage());
        assertEquals(List.of("rejected:" + id + ":Busy season"), handler.calls);
        assertEquals("REJECTED", requests.rows.get(id).stage);
        RequestHistoryResponse last = historyOf(id).getLast();
        assertEquals(HistoryAction.REJECTED, last.action());
        assertEquals("Busy season", last.comment());
    }

    @Test
    void anotherEmployeeCannotActOnTheHeadStep() {
        long id = atHead();
        assertThrows(AccessDeniedException.class, () -> act(id, other, "EMPLOYEE", "HEAD", StepOutcome.APPROVE, null, null));
    }

    // ----- requester holds a step -----------------------------------------------------------------------------

    @Test
    void aBranchManagerRequesterSkipsTheBranchStepAndMustNameTheDepartment() {
        long id = newRequest(branchManager);
        assertEquals("TARGET_DEPARTMENT_REQUIRED", ruleOf(() -> submit(id, branchManager, null)));

        long id2 = newRequest(branchManager);
        WorkflowStatusResponse s = submit(id2, branchManager, DEPT);
        assertEquals("PENDING_SECRETARY", s.stage());
        assertEquals(DEPT, requests.rows.get(id2).targetDepartmentId);
        assertEquals(List.of(HistoryAction.SUBMITTED, HistoryAction.AUTO_ROUTED), actions(id2));
        assertEquals("SKIP_REQUESTER_IS_APPROVER", reasonCode(id2, 1));
    }

    @Test
    void anOrdinaryEmployeesDepartmentIsDroppedAndTheBranchManagerPicksIt() {
        long id = newRequest(requester);
        WorkflowStatusResponse s = submit(id, requester, DEPT);

        assertEquals("PENDING_BRANCH", s.stage());
        assertNull(requests.rows.get(id).targetDepartmentId);
        assertEquals("PENDING_SECRETARY",
            act(id, branchManager, "EMPLOYEE", "BRANCH", StepOutcome.FORWARD, DEPT, null).stage());
        assertEquals(DEPT, requests.rows.get(id).targetDepartmentId);
    }

    @Test
    void aBranchManagersOwnDepartmentIsValidatedAndKept() {
        long id = newRequest(branchManager);
        assertEquals("DEPARTMENT_INACTIVE", ruleOf(() -> submit(id, branchManager, INACTIVE_DEPT)));
        assertThrows(ResourceNotFoundException.class, () -> submit(newRequest(branchManager), branchManager, UNKNOWN_DEPT));
        assertEquals("PENDING_SECRETARY", submit(newRequest(branchManager), branchManager, DEPT).stage());
    }

    @Test
    void aBranchManagerRequesterWithoutAnExplicitDepartmentUsesTheirOwn() {
        when(organisation.departmentOf(branchManager)).thenReturn(Optional.of(DEPT));
        long id = newRequest(branchManager);

        WorkflowStatusResponse s = submit(id, branchManager, null);

        assertEquals("PENDING_SECRETARY", s.stage());
        assertEquals(DEPT, requests.rows.get(id).targetDepartmentId);
    }

    /**
     * The head changes in a committed transaction (two events in it): the waiting request names the new head only
     * after the commit, then HR_ADMIN once there is no head. Committed, so the refresh (its own transactions) runs.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aChangedHeadOrNoHeadIsStoredOnTheWaitingRequestOnceTheChangeCommits() {
        Committed c = commitFlow(3, headOnlyFlow());
        try {
            long requesterId = c.people()[0], oldHead = c.people()[1], newHead = c.people()[2];
            when(organisation.departmentExists(DEPT)).thenReturn(true);
            when(organisation.isDepartmentActive(DEPT)).thenReturn(true);
            when(organisation.headOf(DEPT)).thenReturn(Optional.of(oldHead));
            long id = newRequest(requesterId);
            c.requestIds().add(id);
            as(requesterId, "EMPLOYEE");
            assertEquals("PENDING_HEAD", tx.execute(s -> workflow.submit(RequestType.LEAVE, id, requesterId, DEPT,
                Map.of("totalDays", 1))).stage());
            assertEquals(oldHead, requests.rows.get(id).approverEmployeeId);

            when(organisation.headOf(DEPT)).thenReturn(Optional.of(newHead));
            tx.executeWithoutResult(s -> {
                events.publishEvent(new com.hrms.employee.service.OrganisationChanged());
                events.publishEvent(new com.hrms.employee.service.OrganisationChanged());
                assertEquals(oldHead, requests.rows.get(id).approverEmployeeId, "not before the commit");
            });
            assertEquals(newHead, requests.rows.get(id).approverEmployeeId);
            assertNull(requests.rows.get(id).approverRole);

            when(organisation.headOf(DEPT)).thenReturn(Optional.empty());
            tx.executeWithoutResult(s -> events.publishEvent(new com.hrms.employee.service.OrganisationChanged()));
            assertNull(requests.rows.get(id).approverEmployeeId);
            assertEquals("HR_ADMIN", requests.rows.get(id).approverRole);
            assertEquals("PENDING_HEAD", requests.rows.get(id).stage);
        } finally {
            removeCommitted(c);
        }
    }

    /** Rolled back: the refresh never runs, nothing is re-pointed. */
    @Test
    void aChangeThatRollsBackRefreshesNothing() {
        long id = atHead();
        when(organisation.headOf(DEPT)).thenReturn(Optional.of(other));
        events.publishEvent(new com.hrms.employee.service.OrganisationChanged());
        assertEquals(head, requests.rows.get(id).approverEmployeeId);
    }

    @Test
    void aHeadRequesterSkipsTheHeadStepToTheGeneralManager() {
        long id = newRequest(head);
        submit(id, head, null);
        act(id, branchManager, "EMPLOYEE", "BRANCH", StepOutcome.FORWARD, DEPT, null);

        WorkflowStatusResponse s = act(id, secretary, "EMPLOYEE", "SECRETARY", StepOutcome.FORWARD, null, null);

        assertEquals("PENDING_GM", s.stage());
        RequestHistoryResponse skip = historyOf(id).getLast();
        assertEquals(HistoryAction.AUTO_ROUTED, skip.action());
        assertEquals("HEAD", skip.stepKey());
    }

    @Test
    void anHrAdminRequesterCannotProcessTheirOwnLeaveButAnotherHrAdminCan() {
        long id = newRequest(hr2);
        as(hr2, "HR_ADMIN");
        workflow.submit(RequestType.LEAVE, id, hr2, null, Map.of("totalDays", new BigDecimal("1.5")));
        act(id, branchManager, "EMPLOYEE", "BRANCH", StepOutcome.FORWARD, DEPT, null);
        act(id, secretary, "EMPLOYEE", "SECRETARY", StepOutcome.FORWARD, null, null);
        act(id, head, "EMPLOYEE", "HEAD", StepOutcome.APPROVE, null, null);
        act(id, gm, "GENERAL_MANAGER", "GM", StepOutcome.APPROVE, null, null);

        assertEquals("SELF_APPROVAL", ruleOf(() -> act(id, hr2, "HR_ADMIN", "HR", StepOutcome.APPROVE, null, null)));
        assertEquals("APPROVED", act(id, hr1, "HR_ADMIN", "HR", StepOutcome.APPROVE, null, null).stage());
    }

    // ----- override -------------------------------------------------------------------------------------------

    @Test
    void aMissingHeadWaitsForAnHrAdminOverrideWithAReason() {
        when(organisation.headOf(DEPT)).thenReturn(Optional.empty());
        long id = atHead();
        assertEquals("PENDING_HEAD", requests.rows.get(id).stage);
        assertNull(requests.rows.get(id).approverEmployeeId);
        assertEquals("HR_ADMIN", requests.rows.get(id).approverRole); // so HR_ADMIN finds it in the inbox
        assertEquals(HistoryAction.AUTO_ROUTED, historyOf(id).getLast().action());
        assertEquals("NO_APPROVER", reasonCode(id, 3));

        as(other, "EMPLOYEE");
        assertThrows(AccessDeniedException.class, () -> workflow.override(RequestType.LEAVE, id,
            new WorkflowOverrideRequest("HEAD", StepOutcome.APPROVE, null, "Head on leave")));
        as(hr1, "HR_ADMIN");
        assertEquals("REASON_REQUIRED", ruleOf(() -> workflow.override(RequestType.LEAVE, id,
            new WorkflowOverrideRequest("HEAD", StepOutcome.APPROVE, null, " "))));

        WorkflowStatusResponse s = workflow.override(RequestType.LEAVE, id,
            new WorkflowOverrideRequest("HEAD", StepOutcome.APPROVE, null, "Head post vacant"));

        assertEquals("PENDING_GM", s.stage());
        RequestHistoryResponse row = historyOf(id).getLast();
        assertEquals(HistoryAction.OVERRIDDEN, row.action());
        assertEquals("Head post vacant", row.comment());
        assertEquals(hr1, row.actorEmployeeId());
        assertEquals("HR_ADMIN", row.actorRole());
        assertEquals("APPROVE", reasonCode(id, 4));
    }

    @Test
    void aRequesterWithoutBranchManagerWaitsAndTheOverrideMustNameTheDepartment() {
        when(organisation.branchManagerOf(requester)).thenReturn(Optional.empty());
        long id = newRequest(requester);
        assertEquals("PENDING_BRANCH", submit(id, requester, null).stage());
        assertEquals("NO_APPROVER", reasonCode(id, 1));
        as(branchManager, "EMPLOYEE");
        assertThrows(AccessDeniedException.class, () -> workflow.act(RequestType.LEAVE, id,
            new WorkflowActionRequest("BRANCH", StepOutcome.FORWARD, DEPT, null)));

        as(hr1, "HR_ADMIN");
        assertEquals("TARGET_DEPARTMENT_REQUIRED", ruleOf(() -> workflow.override(RequestType.LEAVE, id,
            new WorkflowOverrideRequest("BRANCH", StepOutcome.FORWARD, null, "No branch yet"))));
        WorkflowStatusResponse s = workflow.override(RequestType.LEAVE, id,
            new WorkflowOverrideRequest("BRANCH", StepOutcome.FORWARD, DEPT, "No branch yet"));

        assertEquals("PENDING_SECRETARY", s.stage());
        assertEquals(DEPT, requests.rows.get(id).targetDepartmentId);
    }

    // ----- concurrency ----------------------------------------------------------------------------------------

    @Test
    void aSecondIdenticalActIsAConflictAndWritesNoHistory() {
        long id = newRequest(requester);
        submit(id, requester, null);
        act(id, branchManager, "EMPLOYEE", "BRANCH", StepOutcome.FORWARD, DEPT, null);
        int rows = actions(id).size();

        assertThrows(OptimisticLockingFailureException.class,
            () -> act(id, branchManager, "EMPLOYEE", "BRANCH", StepOutcome.FORWARD, DEPT, null));
        assertEquals(rows, actions(id).size());
        assertEquals("PENDING_SECRETARY", requests.rows.get(id).stage);
    }

    // ----- cancel ---------------------------------------------------------------------------------------------

    @Test
    void theRequesterCancelsAtAnyPendingStageAndNobodyElseCan() {
        long early = newRequest(requester);
        submit(early, requester, null);
        as(other, "HR_ADMIN");
        assertThrows(AccessDeniedException.class, () -> workflow.cancel(RequestType.LEAVE, early));
        as(requester, "EMPLOYEE");
        assertEquals("CANCELLED", workflow.cancel(RequestType.LEAVE, early).stage());

        long late = atHead();
        act(late, head, "EMPLOYEE", "HEAD", StepOutcome.APPROVE, null, null);
        act(late, gm, "GENERAL_MANAGER", "GM", StepOutcome.APPROVE, null, null);
        as(requester, "EMPLOYEE");
        assertEquals("CANCELLED", workflow.cancel(RequestType.LEAVE, late).stage());

        assertEquals(List.of("cancelled:" + early, "cancelled:" + late), handler.calls);
        assertEquals("CANCELLED", requests.rows.get(late).stage);
        assertEquals(HistoryAction.CANCELLED, historyOf(late).getLast().action());
        assertEquals("HR", historyOf(late).getLast().stepKey());
        assertEquals(0, runtimeService.createProcessInstanceQuery()
            .processInstanceId(requests.rows.get(late).processInstanceId).count());
        assertThrows(OptimisticLockingFailureException.class, () -> workflow.cancel(RequestType.LEAVE, late));
        assertThrows(OptimisticLockingFailureException.class,
            () -> act(late, hr1, "HR_ADMIN", "HR", StepOutcome.APPROVE, null, null));
    }

    // ----- conditions -----------------------------------------------------------------------------------------

    @Test
    void aTotalDaysConditionRoutesShortLeaveFromHeadStraightToHr() {
        DefaultWorkflowFactory.Flow flow = BpmnGeneratorTest.conditionalFlow(">", "3"); // > 3 -> GM, otherwise HR
        definitionImpl.publish(RequestType.LEAVE, flow.steps(), flow.routes());

        assertEquals("PENDING_HR", headApprovesLeaveOf(new BigDecimal("3")));
        assertEquals("PENDING_HR", headApprovesLeaveOf(new BigDecimal("0.5")));
        assertEquals("PENDING_GM", headApprovesLeaveOf(new BigDecimal("3.5")));
    }

    private String headApprovesLeaveOf(BigDecimal days) {
        long id = newRequest(requester);
        as(requester, "EMPLOYEE");
        assertEquals("PENDING_HEAD",
            workflow.submit(RequestType.LEAVE, id, requester, DEPT, Map.of("totalDays", days)).stage());
        return act(id, head, "EMPLOYEE", "HEAD", StepOutcome.APPROVE, null, null).stage();
    }

    // ----- participant check (spec 10) ------------------------------------------------------------------------

    @Test
    void anUnrelatedEmployeeGetsTheIdentical403WhateverTheRequestsState() {
        long atStep = atHead();
        long closed = newRequest(requester);
        submit(closed, requester, null);
        as(requester, "EMPLOYEE");
        workflow.cancel(RequestType.LEAVE, closed);
        long legacy = newRequest(requester); // never submitted: no process instance
        long unknown = REQUEST_IDS.incrementAndGet();

        as(other, "EMPLOYEE");
        List<String> messages = new ArrayList<>();
        for (long id : List.of(atStep, closed, legacy, unknown)) {
            messages.add(assertThrows(AccessDeniedException.class, () -> workflow.act(RequestType.LEAVE, id,
                new WorkflowActionRequest("BRANCH", StepOutcome.FORWARD, DEPT, null))).getMessage());
            messages.add(assertThrows(AccessDeniedException.class, () -> workflow.override(RequestType.LEAVE, id,
                new WorkflowOverrideRequest("BRANCH", StepOutcome.FORWARD, DEPT, "x"))).getMessage());
            messages.add(assertThrows(AccessDeniedException.class,
                () -> workflow.cancel(RequestType.LEAVE, id)).getMessage());
            messages.add(assertThrows(AccessDeniedException.class,
                () -> workflow.history(RequestType.LEAVE, id)).getMessage());
        }
        assertEquals(1, messages.stream().distinct().count(), messages.toString());

        as(hr1, "HR_MANAGER");
        assertThrows(ResourceNotFoundException.class, () -> workflow.history(RequestType.LEAVE, unknown));
    }

    @Test
    void theManagerOfAnotherBranchIsUnrelated() {
        when(organisation.branchManagerOf(gm)).thenReturn(Optional.of(other)); // other manages gm's branch
        long id = newRequest(requester);
        submit(id, requester, null);

        as(other, "EMPLOYEE");
        assertThrows(AccessDeniedException.class, () -> workflow.act(RequestType.LEAVE, id,
            new WorkflowActionRequest("BRANCH", StepOutcome.FORWARD, DEPT, null)));
        assertThrows(AccessDeniedException.class, () -> workflow.history(RequestType.LEAVE, id));
    }

    @Test
    void historyIsReadableByTheRequesterAParticipantAStepHolderAndHrButNotOthers() {
        long id = newRequest(requester);
        submit(id, requester, null);
        act(id, branchManager, "EMPLOYEE", "BRANCH", StepOutcome.FORWARD, DEPT, null);

        Object[][] allowed = {{requester, "EMPLOYEE"}, {branchManager, "EMPLOYEE"}, {secretary, "EMPLOYEE"},
            {hr1, "HR_MANAGER"}, {other, "GENERAL_MANAGER"}, {hr2, "HR_ADMIN"}};
        for (Object[] who : allowed) {
            as((Long) who[0], (String) who[1]);
            assertEquals(2, workflow.history(RequestType.LEAVE, id).size(), who[0] + " " + who[1]);
        }
        for (String role : List.of("EMPLOYEE", "SYSTEM_ADMIN")) {
            as(other, role);
            assertThrows(AccessDeniedException.class, () -> workflow.history(RequestType.LEAVE, id), role);
        }
    }

    @Test
    void statusShowsTheLiveStepToParticipantsAndTheClosedStageAfterwards() {
        long id = newRequest(requester);
        submit(id, requester, null);

        as(requester, "EMPLOYEE");
        WorkflowStatusResponse live = workflow.status(RequestType.LEAVE, id);
        assertEquals("PENDING_BRANCH", live.stage());
        assertEquals("BRANCH", live.stepKey());
        assertEquals(requester, live.requesterEmployeeId());
        assertEquals("Fake leave", live.summary());
        assertFalse(live.pickTargetDepartment());
        assertEquals(List.of(), live.allowedOutcomes());

        as(branchManager, "EMPLOYEE");
        WorkflowStatusResponse holder = workflow.status(RequestType.LEAVE, id);
        assertTrue(holder.pickTargetDepartment());
        assertEquals(List.of(StepOutcome.FORWARD, StepOutcome.REJECT), holder.allowedOutcomes());

        as(hr1, "HR_MANAGER"); // an overseer who does not hold the step
        assertEquals(List.of(), workflow.status(RequestType.LEAVE, id).allowedOutcomes());

        as(other, "EMPLOYEE");
        assertThrows(AccessDeniedException.class, () -> workflow.status(RequestType.LEAVE, id));

        as(requester, "EMPLOYEE");
        workflow.cancel(RequestType.LEAVE, id);
        WorkflowStatusResponse closed = workflow.status(RequestType.LEAVE, id);
        assertEquals("CANCELLED", closed.stage());
        assertNull(closed.stepKey());
        assertTrue(closed.allowedOutcomes().isEmpty());

        long legacy = newRequest(requester);
        as(hr1, "HR_MANAGER");
        assertEquals("NOT_IN_WORKFLOW", ruleOf(() -> workflow.status(RequestType.LEAVE, legacy)));
    }

    @Test
    void aPastActorSeesNoOutcomesOnALaterStep() {
        long id = newRequest(requester);
        submit(id, requester, null);
        act(id, branchManager, "EMPLOYEE", "BRANCH", StepOutcome.FORWARD, DEPT, null);

        as(branchManager, "EMPLOYEE"); // acted before, now only a participant
        WorkflowStatusResponse s = workflow.status(RequestType.LEAVE, id);
        assertEquals("PENDING_SECRETARY", s.stage());
        assertEquals(List.of(), s.allowedOutcomes());
        as(secretary, "EMPLOYEE");
        assertEquals(List.of(StepOutcome.FORWARD), workflow.status(RequestType.LEAVE, id).allowedOutcomes());
    }

    @Test
    void targetDepartmentsAreOnlyForTheHolderOfTheBranchStep() {
        String run = Long.toString(System.nanoTime(), 36);
        long active = departmentRepository.saveAndFlush(com.hrms.employee.entity.Department.builder()
            .code("TA" + run.substring(0, Math.min(6, run.length()))).name("Ta active " + run).nameAr("ق")
            .isActive(true).build()).getDeptId();
        long inactive = departmentRepository.saveAndFlush(com.hrms.employee.entity.Department.builder()
            .code("TI" + run.substring(0, Math.min(6, run.length()))).name("Ta inactive " + run).nameAr("ق")
            .isActive(false).build()).getDeptId();
        long id = newRequest(requester);
        submit(id, requester, null);

        as(branchManager, "EMPLOYEE");
        List<Long> ids = workflow.targetDepartments(RequestType.LEAVE, id).stream()
            .map(com.hrms.employee.dto.response.DepartmentLookupResponse::getId).toList();
        assertTrue(ids.contains(active));
        assertFalse(ids.contains(inactive));

        for (Object[] who : new Object[][]{{requester, "EMPLOYEE"}, {secretary, "EMPLOYEE"}, {head, "EMPLOYEE"},
            {other, "EMPLOYEE"}, {hr1, "HR_MANAGER"}, {other, "SYSTEM_ADMIN"}}) {
            as((Long) who[0], (String) who[1]);
            assertThrows(AccessDeniedException.class, () -> workflow.targetDepartments(RequestType.LEAVE, id),
                who[0] + " " + who[1]);
        }

        act(id, branchManager, "EMPLOYEE", "BRANCH", StepOutcome.FORWARD, DEPT, null);
        as(secretary, "EMPLOYEE"); // holds a step that does not pick a department
        assertThrows(AccessDeniedException.class, () -> workflow.targetDepartments(RequestType.LEAVE, id));
        as(branchManager, "EMPLOYEE");
        assertThrows(AccessDeniedException.class, () -> workflow.targetDepartments(RequestType.LEAVE, id));

        as(requester, "EMPLOYEE");
        workflow.cancel(RequestType.LEAVE, id);
        as(branchManager, "EMPLOYEE");
        assertThrows(AccessDeniedException.class, () -> workflow.targetDepartments(RequestType.LEAVE, id));
    }

    // ----- more authorization ---------------------------------------------------------------------------------

    /** An employee request waiting at HEAD, GM or HR. */
    private long atStep(String step) {
        long id = atHead();
        if (!"HEAD".equals(step)) {
            act(id, head, "EMPLOYEE", "HEAD", StepOutcome.APPROVE, null, null);
        }
        if ("HR".equals(step)) {
            act(id, gm, "GENERAL_MANAGER", "GM", StepOutcome.APPROVE, null, null);
        }
        return id;
    }

    @Test
    void hrManagerSystemAdminAndGeneralManagerCannotProcessTheHrStep() {
        long id = atStep("HR");
        for (String role : List.of("HR_MANAGER", "SYSTEM_ADMIN", "GENERAL_MANAGER")) {
            assertThrows(AccessDeniedException.class,
                () -> act(id, other, role, "HR", StepOutcome.APPROVE, null, null), role);
        }
        assertEquals("PENDING_HR", requests.rows.get(id).stage);
    }

    @Test
    void anEmployeeCannotDecideTheGmStep() {
        long id = atStep("GM");
        assertThrows(AccessDeniedException.class,
            () -> act(id, other, "EMPLOYEE", "GM", StepOutcome.APPROVE, null, null));
        assertThrows(AccessDeniedException.class,
            () -> act(id, head, "EMPLOYEE", "GM", StepOutcome.APPROVE, null, null), "past actor without the role");
    }

    @Test
    void overrideIsRefusedOnRoleStepsAndWhereSomeoneOtherThanTheRequesterHoldsTheStep() {
        long atHeadStep = atStep("HEAD"); // the head is resolved
        as(hr1, "HR_ADMIN");
        assertThrows(AccessDeniedException.class, () -> workflow.override(RequestType.LEAVE, atHeadStep,
            new WorkflowOverrideRequest("HEAD", StepOutcome.APPROVE, null, "Head away")));
        long atGm = atStep("GM");
        as(hr1, "HR_ADMIN");
        assertThrows(AccessDeniedException.class, () -> workflow.override(RequestType.LEAVE, atGm,
            new WorkflowOverrideRequest("GM", StepOutcome.APPROVE, null, "GM away")));
    }

    @Test
    void theRequesterCannotOverrideTheirOwnRequest() {
        when(organisation.headOf(DEPT)).thenReturn(Optional.empty());
        long id = newRequest(hr2);
        as(hr2, "HR_ADMIN");
        workflow.submit(RequestType.LEAVE, id, hr2, null, Map.of("totalDays", 1));
        act(id, branchManager, "EMPLOYEE", "BRANCH", StepOutcome.FORWARD, DEPT, null);
        act(id, secretary, "EMPLOYEE", "SECRETARY", StepOutcome.FORWARD, null, null);

        as(hr2, "HR_ADMIN");
        assertEquals("SELF_APPROVAL", ruleOf(() -> workflow.override(RequestType.LEAVE, id,
            new WorkflowOverrideRequest("HEAD", StepOutcome.APPROVE, null, "I am HR"))));
        assertEquals("PENDING_HEAD", requests.rows.get(id).stage);
    }

    @Test
    void anOverrideRejectEndsTheFlowAndPassesTheReason() {
        when(organisation.headOf(DEPT)).thenReturn(Optional.empty());
        long id = atHead();
        as(hr1, "HR_ADMIN");

        assertEquals("REJECTED", workflow.override(RequestType.LEAVE, id,
            new WorkflowOverrideRequest("HEAD", StepOutcome.REJECT, null, "Department closed")).stage());
        assertEquals(List.of("rejected:" + id + ":Department closed"), handler.calls);
        assertEquals(HistoryAction.OVERRIDDEN, historyOf(id).getLast().action());
    }

    /** A head-only custom flow: the head's own leave cannot be skipped without approving, so it waits for HR. */
    @Test
    void aStepThatCannotBeSkippedWithoutApprovingWaitsForAnHrAdminOverride() {
        var headStep = DefaultWorkflowFactory.step("HEAD", 1, "Head", "Head", ApproverType.DEPARTMENT_HEAD, null);
        definitionImpl.publish(RequestType.LEAVE, List.of(headStep), List.of(
            DefaultWorkflowFactory.route(headStep, StepOutcome.APPROVE, "APPROVED", 1),
            DefaultWorkflowFactory.route(headStep, StepOutcome.REJECT, "REJECTED", 2)));
        long id = newRequest(head);
        as(head, "EMPLOYEE");
        assertEquals("PENDING_HEAD",
            workflow.submit(RequestType.LEAVE, id, head, DEPT, Map.of("totalDays", 1)).stage());
        assertEquals("SKIP_WOULD_APPROVE", reasonCode(id, 1));
        assertNull(requests.rows.get(id).approverEmployeeId);
        assertEquals("HR_ADMIN", requests.rows.get(id).approverRole);
        assertEquals("SELF_APPROVAL",
            ruleOf(() -> act(id, head, "EMPLOYEE", "HEAD", StepOutcome.APPROVE, null, null)));

        as(hr1, "HR_ADMIN");
        assertEquals("APPROVED", workflow.override(RequestType.LEAVE, id,
            new WorkflowOverrideRequest("HEAD", StepOutcome.APPROVE, null, "Own leave of the head")).stage());
        assertEquals(List.of("approved:" + id), handler.calls);
    }

    // ----- committed (not under the test transaction): rollback and races --------------------------------------

    /** Committed fixture: a single-step HR flow (process or reject), the flag on, and real employee rows. */
    private record Committed(WorkflowDefinition def, long[] people, List<Long> requestIds) {
    }

    private Committed commitHrOnlyFlow(int employeeCount) {
        return commitFlow(employeeCount, hrOnlyFlow());
    }

    private Committed commitFlow(int employeeCount, DefaultWorkflowFactory.Flow flow) {
        String run = Long.toString(System.nanoTime(), 36);
        long[] people = tx.execute(s -> {
            long[] p = new long[employeeCount];
            for (int i = 0; i < employeeCount; i++) {
                p[i] = employee("c" + i, run);
            }
            return p;
        });
        WorkflowDefinition def = tx.execute(s -> {
            enable(true);
            return definitionImpl.publish(RequestType.LEAVE, flow.steps(), flow.routes());
        });
        return new Committed(def, people, new ArrayList<>());
    }

    /** A committed request by {@code requesterId}, waiting at HR. */
    private long committedAtHr(Committed c, long requesterId) {
        long id = newRequest(requesterId);
        c.requestIds().add(id);
        as(requesterId, "EMPLOYEE");
        assertEquals("PENDING_HR", tx.execute(s -> workflow.submit(RequestType.LEAVE, id, requesterId, null,
            Map.of("totalDays", 2))).stage());
        return id;
    }

    /**
     * The completion handler fails while HR processes: the history row, the audit row and the Flowable completion
     * are all rolled back and the HR task stays open. The fake port is NOT transactional, so its stage is no
     * evidence of the rollback (it is unchanged only because markTerminal runs after the handler); the proof is the
     * database history, the audit table and the Flowable task. Task 8 repeats this with the real leave port.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aFailingCompletionHandlerRollsBackTheWholeHrAction() {
        Committed c = commitHrOnlyFlow(2);
        try {
            long id = committedAtHr(c, c.people()[0]);
            handler.failOnApproved = true;
            as(c.people()[1], "HR_ADMIN");

            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> tx.execute(s ->
                workflow.act(RequestType.LEAVE, id, new WorkflowActionRequest("HR", StepOutcome.APPROVE, null, null))));
            assertEquals("balance row missing", failure.getMessage());

            assertEquals(List.of(HistoryAction.SUBMITTED), tx.execute(s -> actions(id)));
            assertEquals("HR", taskService.createTaskQuery()
                .processInstanceId(requests.rows.get(id).processInstanceId).singleResult().getTaskDefinitionKey());
            assertEquals(0, jdbc.queryForObject("select count(*) from AUDIT_EVENTS where TARGET_TYPE = 'LEAVE'"
                + " and TARGET_ID = ?", Long.class, String.valueOf(id)));
        } finally {
            removeCommitted(c);
        }
    }

    /** Two HR admins process the same task at once, each in its own committed transaction: exactly one wins. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void twoConcurrentApproversOfTheSameTaskGiveOneSuccessAndOneConflict() throws Exception {
        Committed c = commitHrOnlyFlow(3);
        try {
            long id = committedAtHr(c, c.people()[0]);
            WorkflowActionRequest approve = new WorkflowActionRequest("HR", StepOutcome.APPROVE, null, null);
            List<Object> results = race(
                () -> {
                    as(c.people()[1], "HR_ADMIN");
                    return workflow.act(RequestType.LEAVE, id, approve);
                },
                () -> {
                    as(c.people()[2], "HR_ADMIN");
                    return workflow.act(RequestType.LEAVE, id, approve);
                });

            assertOneWinnerOneConflict(results);
            assertEquals(1, countRows(id, "APPROVED"));
            assertEquals(List.of("approved:" + id), handler.calls);
        } finally {
            removeCommitted(c);
        }
    }

    /** The requester cancels while HR processes: exactly one of the two takes effect. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cancelRacingAnApprovalGivesOneSuccessAndOneConflict() throws Exception {
        Committed c = commitHrOnlyFlow(2);
        try {
            long id = committedAtHr(c, c.people()[0]);
            List<Object> results = race(
                () -> {
                    as(c.people()[0], "EMPLOYEE");
                    return workflow.cancel(RequestType.LEAVE, id);
                },
                () -> {
                    as(c.people()[1], "HR_ADMIN");
                    return workflow.act(RequestType.LEAVE, id,
                        new WorkflowActionRequest("HR", StepOutcome.APPROVE, null, null));
                });

            assertOneWinnerOneConflict(results);
            assertEquals(1, countRows(id, "APPROVED") + countRows(id, "CANCELLED"));
            assertEquals(1, handler.calls.size(), handler.calls.toString());
        } finally {
            removeCommitted(c);
        }
    }

    /** Runs both actions on two threads, released together by a latch, each in its own transaction. */
    private List<Object> race(Callable<WorkflowStatusResponse> a, Callable<WorkflowStatusResponse> b) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<WorkflowStatusResponse> action : List.of(a, b)) {
                futures.add(pool.submit(() -> runInOwnTransaction(start, action)));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> f : futures) {
                results.add(f.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    /** The action's result, or the exception it ended with. */
    private Object runInOwnTransaction(CountDownLatch start, Callable<WorkflowStatusResponse> action)
            throws InterruptedException {
        start.await();
        try {
            return tx.execute(s -> {
                try {
                    return action.call();
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        } catch (RuntimeException e) {
            return e;
        } finally {
            SecurityContextHolder.clearContext();
            caller.remove();
        }
    }

    private static void assertOneWinnerOneConflict(List<Object> results) {
        long wins = results.stream().filter(r -> r instanceof WorkflowStatusResponse).count();
        long conflicts = results.stream().filter(r -> r instanceof OptimisticLockingFailureException).count();
        assertEquals(1, wins, results.toString());
        assertEquals(1, conflicts, results.toString());
    }

    private long countRows(long requestId, String action) {
        return jdbc.queryForObject("select count(*) from REQUEST_HISTORY where REQUEST_TYPE = 'LEAVE'"
            + " and REQUEST_ID = ? and ACTION = ?", Long.class, requestId, action);
    }

    /** A single HR step: process or reject. */
    private static DefaultWorkflowFactory.Flow hrOnlyFlow() {
        var hr = DefaultWorkflowFactory.step("HR", 1, "HR", "HR", ApproverType.ROLE, "HR_ADMIN");
        return new DefaultWorkflowFactory.Flow(List.of(hr), List.of(
            DefaultWorkflowFactory.route(hr, StepOutcome.APPROVE, "APPROVED", 1),
            DefaultWorkflowFactory.route(hr, StepOutcome.REJECT, "REJECTED", 2)));
    }

    /** A single department-head step: approve or reject. */
    private static DefaultWorkflowFactory.Flow headOnlyFlow() {
        var headStep = DefaultWorkflowFactory.step("HEAD", 1, "Head", "Head", ApproverType.DEPARTMENT_HEAD, null);
        return new DefaultWorkflowFactory.Flow(List.of(headStep), List.of(
            DefaultWorkflowFactory.route(headStep, StepOutcome.APPROVE, "APPROVED", 1),
            DefaultWorkflowFactory.route(headStep, StepOutcome.REJECT, "REJECTED", 2)));
    }

    /** Removes everything the committed fixture and its requests left, including the Flowable deployment. */
    private void removeCommitted(Committed c) {
        SecurityContextHolder.clearContext();
        long defId = c.def().getDefinitionId();
        tx.executeWithoutResult(s -> {
            for (long requestId : c.requestIds()) {
                jdbc.update("delete from REQUEST_HISTORY where REQUEST_TYPE = 'LEAVE' and REQUEST_ID = ?", requestId);
                jdbc.update("delete from AUDIT_EVENTS where TARGET_TYPE = 'LEAVE' and TARGET_ID = ?",
                    String.valueOf(requestId));
            }
            jdbc.update("delete from WORKFLOW_ROUTES where STEP_ID in (select STEP_ID from WORKFLOW_STEPS"
                + " where DEFINITION_ID = ?)", defId);
            jdbc.update("delete from WORKFLOW_STEPS where DEFINITION_ID = ?", defId);
            jdbc.update("delete from AUDIT_EVENTS where ACTION = 'WORKFLOW_PUBLISHED' and TARGET_ID = ?",
                String.valueOf(defId));
            jdbc.update("delete from WORKFLOW_DEFINITIONS where DEFINITION_ID = ?", defId);
            jdbc.update("update WORKFLOW_SETTINGS set SETTING_VALUE = 'false' where SETTING_KEY = 'WORKFLOW_ENABLED_LEAVE'");
            for (long p : c.people()) {
                jdbc.update("delete from EMPLOYEES where EMPLOYEE_ID = ?", p);
            }
        });
        repositoryService.deleteDeployment(c.def().getFlowableDeploymentId(), true);
    }

    // ----- fakes ----------------------------------------------------------------------------------------------

    // Deliberately not @TestConfiguration: that is component-scanned into every other test's context and then clashes
    // with the real leave beans. It is imported explicitly by this class instead.
    static class Fakes {
        // Named like the real leave beans so that, in this test only, the in-memory fakes replace them.
        @Bean("leaveWorkflowAccess")
        FakeRequestAccess fakeRequestAccess() {
            return new FakeRequestAccess();
        }

        @Bean("leaveCompletionHandler")
        FakeCompletionHandler fakeCompletionHandler() {
            return new FakeCompletionHandler();
        }
    }

    /** The request table, in memory. Not transactional: it shows what the service asked for. */
    static class FakeRequestAccess implements WorkflowRequestAccess {

        static class Row {
            final long requester;
            Long targetDepartmentId;
            Long definitionId;
            String processInstanceId;
            String stage;
            String stepKey;
            String approverType;
            Long approverEmployeeId;
            String approverRole;

            Row(long requester) {
                this.requester = requester;
            }
        }

        final Map<Long, Row> rows = new ConcurrentHashMap<>();

        private Row row(long id) {
            Row r = rows.get(id);
            if (r == null) {
                throw new ResourceNotFoundException("Request", "id", id);
            }
            return r;
        }

        @Override
        public RequestType type() {
            return RequestType.LEAVE;
        }

        @Override
        public long requesterEmployeeId(long requestId) {
            return row(requestId).requester;
        }

        @Override
        public Long targetDepartmentId(long requestId) {
            return row(requestId).targetDepartmentId;
        }

        @Override
        public Brief brief(long requestId) {
            return new Brief(row(requestId).requester, "Fake requester", "Fake leave");
        }

        @Override
        public Optional<String> stage(long requestId) {
            return Optional.ofNullable(row(requestId).stage);
        }

        @Override
        public Optional<String> processInstanceId(long requestId) {
            return Optional.ofNullable(row(requestId).processInstanceId);
        }

        @Override
        public void markEntered(long requestId, Long definitionId, String processInstanceId, Long targetDepartmentId,
                                String stage, String stepKey, String approverType, Long approverEmployeeId,
                                String approverRole) {
            Row r = row(requestId);
            r.definitionId = definitionId;
            r.processInstanceId = processInstanceId;
            r.targetDepartmentId = targetDepartmentId;
            markStage(requestId, stage, stepKey, approverType, approverEmployeeId, approverRole);
        }

        @Override
        public void markStage(long requestId, String stage, String stepKey, String approverType,
                              Long approverEmployeeId, String approverRole) {
            Row r = row(requestId);
            r.stage = stage;
            r.stepKey = stepKey;
            r.approverType = approverType;
            r.approverEmployeeId = approverEmployeeId;
            r.approverRole = approverRole;
        }

        @Override
        public void markTerminal(long requestId, String stage) {
            markStage(requestId, stage, null, null, null, null);
        }

        @Override
        public List<OpenRequest> openRequests(java.util.Collection<String> stages, long afterRequestId, int limit) {
            return rows.entrySet().stream()
                .filter(e -> stages.contains(e.getValue().stage) && e.getKey() > afterRequestId)
                .sorted(Map.Entry.comparingByKey()).limit(limit)
                .map(e -> new OpenRequest(e.getKey(), e.getValue().requester, e.getValue().targetDepartmentId,
                    e.getValue().definitionId, e.getValue().stepKey, e.getValue().stage,
                    e.getValue().approverEmployeeId, e.getValue().approverRole)).toList();
        }
    }

    static class FakeCompletionHandler implements RequestCompletionHandler {
        final List<String> calls = Collections.synchronizedList(new ArrayList<>());
        volatile boolean failOnApproved;

        @Override
        public RequestType type() {
            return RequestType.LEAVE;
        }

        @Override
        public void onApproved(long requestId) {
            if (failOnApproved) {
                throw new IllegalStateException("balance row missing");
            }
            calls.add("approved:" + requestId);
        }

        @Override
        public void onRejected(long requestId, String reason) {
            calls.add("rejected:" + requestId + ":" + reason);
        }

        @Override
        public void onCancelled(long requestId) {
            calls.add("cancelled:" + requestId);
        }
    }
}
