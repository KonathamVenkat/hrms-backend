package com.hrms.leave.service.impl;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.DatabaseTest;
import com.hrms.employee.EmployeeApplication;
import com.hrms.employee.entity.Department;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.DepartmentRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.service.OrganisationLookup;
import com.hrms.leave.dto.request.ApproveLeaveRequest;
import com.hrms.leave.dto.request.CreateLeaveRequest;
import com.hrms.leave.dto.response.LeaveRequestResponse;
import com.hrms.leave.entity.LeaveBalance;
import com.hrms.leave.entity.LeaveRequest;
import com.hrms.leave.entity.LeaveStatus;
import com.hrms.leave.entity.LeaveType;
import com.hrms.leave.repository.LeaveBalanceRepository;
import com.hrms.leave.repository.LeaveRequestRepository;
import com.hrms.leave.repository.LeaveTypeRepository;
import com.hrms.leave.service.LeaveService;
import com.hrms.workflow.dto.request.WorkflowActionRequest;
import com.hrms.workflow.dto.request.WorkflowOverrideRequest;
import com.hrms.workflow.entity.WorkflowDefinition;
import com.hrms.workflow.entity.WorkflowSetting;
import com.hrms.workflow.enums.RequestType;
import com.hrms.workflow.enums.StepOutcome;
import com.hrms.workflow.repository.WorkflowSettingRepository;
import com.hrms.workflow.service.WorkflowDefinitionService;
import com.hrms.workflow.service.WorkflowService;
import jakarta.persistence.EntityManager;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Leave through the real approval workflow on real Oracle + Flowable: the default route, the leave balance moving only
 * when HR processes, and the legacy endpoint staying usable for requests that never entered the flow.
 */
@SpringBootTest(classes = EmployeeApplication.class)
@Transactional
class LeaveWorkflowDbTest extends DatabaseTest {

    static final String TYPE = "ANNUAL";

    @Autowired LeaveService leave;
    @Autowired WorkflowService workflow;
    @Autowired WorkflowDefinitionService definitions;
    @Autowired WorkflowSettingRepository settings;
    @Autowired EmployeeRepository employees;
    @Autowired LeaveRequestRepository requests;
    @Autowired LeaveBalanceRepository balances;
    @Autowired LeaveTypeRepository types;
    @Autowired DepartmentRepository departments;
    @Autowired TaskService taskService;
    @Autowired RepositoryService repositoryService;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired EntityManager em;
    @Autowired com.hrms.workflow.service.InboxService inbox;
    @Autowired org.springframework.context.ApplicationEventPublisher events;
    @MockitoBean OrganisationLookup organisation;
    @MockitoBean EmployeeAccessGuard guard;

    long requester, branchManager, secretary, head, gm, hr;
    private final ThreadLocal<Long> caller = new ThreadLocal<>();
    private LocalDate monday;
    private Long createdTypeId;
    private long dept;

    @BeforeEach
    void setUp() {
        monday = LocalDate.now().plusDays(1);
        while (monday.getDayOfWeek() != DayOfWeek.MONDAY) {
            monday = monday.plusDays(1);
        }
        when(guard.currentEmployeeId()).thenAnswer(i -> caller.get());
        when(guard.isHrAdmin()).thenAnswer(i -> hasRole("ROLE_HR_ADMIN"));
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            seed();
            definitions.publishDefault(RequestType.LEAVE);
            enable(true);
        }
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        caller.remove();
    }

    private static boolean hasRole(String role) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role));
    }

    private void seed() {
        String run = Long.toString(System.nanoTime(), 36);
        requester = employee("req", run);
        branchManager = employee("bm", run);
        secretary = employee("sec", run);
        head = employee("head", run);
        gm = employee("gm", run);
        hr = employee("hr", run);
        for (long e : List.of(requester, secretary, head, gm, hr, branchManager)) {
            when(organisation.branchManagerOf(e)).thenReturn(Optional.of(branchManager));
        }
        dept = departments.saveAndFlush(Department.builder().code("LW" + run.substring(0, Math.min(6, run.length())))
            .name("Lw dept " + run).nameAr("ق").isActive(true).build()).getDeptId();
        when(organisation.secretaryOf(dept)).thenReturn(Optional.of(secretary));
        when(organisation.headOf(dept)).thenReturn(Optional.of(head));
        when(organisation.departmentExists(dept)).thenReturn(true);
        when(organisation.isDepartmentActive(dept)).thenReturn(true);
        if (types.findByCodeIgnoreCase(TYPE).isEmpty()) {
            createdTypeId = types.saveAndFlush(LeaveType.builder().code(TYPE).nameEn("Lw annual " + run).nameAr("ع")
                .createdBy("test").build()).getLeaveTypeId();
        }
        balance(requester, 21.0);
    }

    private long employee(String tag, String run) {
        return employees.saveAndFlush(Employee.builder()
            .employeeCode("LW-" + tag + "-" + run).firstName("Lw").firstNameAr("ع").lastName(tag).lastNameAr("ل")
            .dateOfBirth(LocalDate.of(1990, 1, 1)).gender(Gender.MALE)
            .personalEmail("lw-" + tag + "-" + run + "-p@example.test")
            .workEmail("lw-" + tag + "-" + run + "-w@example.test")
            .hireDate(LocalDate.of(2024, 1, 1)).employmentType(EmploymentType.FULL_TIME).build()).getId();
    }

    private void balance(long employeeId, double total) {
        balances.saveAndFlush(LeaveBalance.builder().employeeId(employeeId).leaveTypeCode(TYPE)
            .year(monday.getYear()).totalDays(total).build());
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

    private LeaveRequestResponse apply() {
        as(requester, "EMPLOYEE");
        return leave.applyLeave(requester, CreateLeaveRequest.builder().leaveTypeCode(TYPE)
            .startDate(monday).endDate(monday.plusDays(4)).reason("Family visit").build(), null);
    }

    private void act(long id, long actor, String role, String step, StepOutcome outcome, Long target, String comment) {
        as(actor, role);
        workflow.act(RequestType.LEAVE, id, new WorkflowActionRequest(step, outcome, target, comment));
    }

    /** Forwarded by the branch manager and the secretary, approved by the head and the GM: waiting at HR. */
    private void toHr(long id) {
        act(id, branchManager, "EMPLOYEE", "BRANCH", StepOutcome.FORWARD, dept, null);
        act(id, secretary, "EMPLOYEE", "SECRETARY", StepOutcome.FORWARD, null, null);
        act(id, head, "EMPLOYEE", "HEAD", StepOutcome.APPROVE, null, null);
        act(id, gm, "GENERAL_MANAGER", "GM", StepOutcome.APPROVE, null, null);
    }

    private LeaveRequest request(long id) {
        em.flush();
        em.clear();
        return requests.findById(id).orElseThrow();
    }

    private LeaveBalance balanceOf(long employeeId) {
        em.flush();
        em.clear();
        return balances.findByEmployeeIdAndLeaveTypeCodeAndYear(employeeId, TYPE, monday.getYear()).orElseThrow();
    }

    // ----- apply ----------------------------------------------------------------------------------------------

    @Test
    void applyingWithTheFlagOnStartsTheFlowAndHoldsThePendingDays() {
        LeaveRequestResponse r = apply();

        assertEquals("PENDING_BRANCH", r.getStage());
        LeaveRequest lr = request(r.getLeaveReqId());
        assertEquals("PENDING_BRANCH", lr.getStage());
        assertEquals("BRANCH", lr.getCurrentStepKey());
        assertEquals(branchManager, lr.getCurrentApproverEmployeeId());
        assertNotNull(lr.getWorkflowDefinitionId());
        assertNotNull(lr.getProcessInstanceId());
        assertEquals(LeaveStatus.PENDING, lr.getStatus());
        assertEquals(r.getTotalDays(), balanceOf(requester).getPendingDays());
        assertEquals(0.0, balanceOf(requester).getUsedDays());
    }

    /** The chunked read of the approver refresh: by stage, after an id, in id order, capped. */
    @Test
    void openRequestsAreReadByStageInIdOrderAfterTheLastId() {
        long id = apply().getLeaveReqId();
        em.flush();
        var page = org.springframework.data.domain.PageRequest.of(0, 1000);

        assertTrue(requests.findOpenAtStages(List.of("PENDING_BRANCH"), id - 1, page).stream()
            .anyMatch(lr -> lr.getLeaveReqId() == id));
        assertTrue(requests.findOpenAtStages(List.of("PENDING_BRANCH"), id, page).stream()
            .noneMatch(lr -> lr.getLeaveReqId() <= id));
        assertTrue(requests.findOpenAtStages(List.of("PENDING_HEAD"), id - 1, page).stream()
            .noneMatch(lr -> lr.getLeaveReqId() == id));
        List<Long> ids = requests.findOpenAtStages(List.of("PENDING_BRANCH", "PENDING_HEAD"), 0,
            org.springframework.data.domain.PageRequest.of(0, 3)).stream().map(LeaveRequest::getLeaveReqId).toList();
        assertTrue(ids.size() <= 3);
        assertEquals(ids.stream().sorted().toList(), ids);
    }

    @Test
    void applyingWithTheFlagOffLeavesTheWorkflowColumnsEmptyAndTheOldEndpointDecides() {
        enable(false);
        LeaveRequestResponse r = apply();

        LeaveRequest lr = request(r.getLeaveReqId());
        assertNull(lr.getStage());
        assertNull(lr.getProcessInstanceId());
        assertNull(lr.getWorkflowDefinitionId());

        as(hr, "HR_ADMIN");
        LeaveRequestResponse done = leave.processLeave(r.getLeaveReqId(),
            ApproveLeaveRequest.builder().action("APPROVED").build(), "hr");
        assertEquals("APPROVED", done.getStatus());
        assertEquals(0.0, balanceOf(requester).getPendingDays());
        assertEquals(r.getTotalDays(), balanceOf(requester).getUsedDays());
    }

    @Test
    void aLegacyRequestCreatedBeforeTheFlagWasTurnedOnCanStillBeApprovedTheOldWay() {
        enable(false);
        LeaveRequestResponse r = apply();
        enable(true);

        as(hr, "HR_ADMIN");
        assertEquals("APPROVED", leave.processLeave(r.getLeaveReqId(),
            ApproveLeaveRequest.builder().action("APPROVED").build(), "hr").getStatus());
    }

    @Test
    void theOldEndpointRefusesARequestThatIsInTheWorkflow() {
        LeaveRequestResponse r = apply();

        as(hr, "HR_ADMIN");
        var ex = assertThrows(BusinessRuleException.class, () -> leave.processLeave(r.getLeaveReqId(),
            ApproveLeaveRequest.builder().action("APPROVED").build(), "hr"));
        assertEquals("USE_WORKFLOW", ex.getRuleCode());
        assertEquals(LeaveStatus.PENDING, request(r.getLeaveReqId()).getStatus());
        assertEquals(r.getTotalDays(), balanceOf(requester).getPendingDays());
    }

    // ----- the old HR list ------------------------------------------------------------------------------------

    @Test
    void theOldPendingListAndCountLeaveOutRequestsThatAreInTheWorkflow() {
        enable(false);
        LeaveRequestResponse legacy = apply(); // monday..friday, the old way
        enable(true);
        as(requester, "EMPLOYEE");
        LeaveRequestResponse inFlow = leave.applyLeave(requester, CreateLeaveRequest.builder().leaveTypeCode(TYPE)
            .startDate(monday.plusDays(7)).endDate(monday.plusDays(8)).reason("Second").build(), null);
        assertEquals("PENDING_BRANCH", inFlow.getStage());

        as(hr, "HR_ADMIN");
        var filter = com.hrms.leave.dto.request.LeaveFilterRequest.builder().status("PENDING").size(100).build();
        List<Long> listed = leave.getAllLeaves(filter).getContent().stream().map(LeaveRequestResponse::getLeaveReqId).toList();
        assertTrue(listed.contains(legacy.getLeaveReqId()));
        assertFalse(listed.contains(inFlow.getLeaveReqId()));
        long legacyPending = requests.findAll().stream()
            .filter(r -> r.getStatus() == LeaveStatus.PENDING && r.getWorkflowDefinitionId() == null).count();
        assertEquals(legacyPending, leave.getPendingCount());
    }

    // ----- the route ------------------------------------------------------------------------------------------

    @Test
    void theDaysMoveFromPendingToUsedOnlyWhenHrProcesses() {
        LeaveRequestResponse r = apply();
        long id = r.getLeaveReqId();

        toHr(id);
        assertEquals("PENDING_HR", request(id).getStage());
        assertEquals(r.getTotalDays(), balanceOf(requester).getPendingDays());
        assertEquals(0.0, balanceOf(requester).getUsedDays());

        act(id, hr, "HR_ADMIN", "HR", StepOutcome.APPROVE, null, "processed");

        LeaveRequest lr = request(id);
        assertEquals(LeaveStatus.APPROVED, lr.getStatus());
        assertEquals("APPROVED", lr.getStage());
        assertNull(lr.getCurrentStepKey());
        assertNotNull(lr.getApprovedAt());
        assertEquals(0.0, balanceOf(requester).getPendingDays());
        assertEquals(r.getTotalDays(), balanceOf(requester).getUsedDays());
    }

    @Test
    void aRejectionByTheHeadReleasesThePendingDaysAndKeepsTheReason() {
        LeaveRequestResponse r = apply();
        long id = r.getLeaveReqId();
        act(id, branchManager, "EMPLOYEE", "BRANCH", StepOutcome.FORWARD, dept, null);
        act(id, secretary, "EMPLOYEE", "SECRETARY", StepOutcome.FORWARD, null, null);

        act(id, head, "EMPLOYEE", "HEAD", StepOutcome.REJECT, null, "Busy season");

        LeaveRequest lr = request(id);
        assertEquals(LeaveStatus.REJECTED, lr.getStatus());
        assertEquals("REJECTED", lr.getStage());
        assertEquals("Busy season", lr.getRejectionReason());
        assertEquals(0.0, balanceOf(requester).getPendingDays());
        assertEquals(0.0, balanceOf(requester).getUsedDays());
    }

    @Test
    void aRejectionByTheGmReleasesThePendingDays() {
        LeaveRequestResponse r = apply();
        long id = r.getLeaveReqId();
        act(id, branchManager, "EMPLOYEE", "BRANCH", StepOutcome.FORWARD, dept, null);
        act(id, secretary, "EMPLOYEE", "SECRETARY", StepOutcome.FORWARD, null, null);
        act(id, head, "EMPLOYEE", "HEAD", StepOutcome.APPROVE, null, null);

        act(id, gm, "GENERAL_MANAGER", "GM", StepOutcome.REJECT, null, "Not now");

        LeaveRequest lr = request(id);
        assertEquals(LeaveStatus.REJECTED, lr.getStatus());
        assertEquals("REJECTED", lr.getStage());
        assertEquals("Not now", lr.getRejectionReason());
        assertEquals(0.0, balanceOf(requester).getPendingDays());
        assertEquals(0.0, balanceOf(requester).getUsedDays());
    }

    @Test
    void aRejectionByHrReleasesThePendingDays() {
        LeaveRequestResponse r = apply();
        long id = r.getLeaveReqId();
        toHr(id);

        act(id, hr, "HR_ADMIN", "HR", StepOutcome.REJECT, null, "Overlaps the audit");

        LeaveRequest lr = request(id);
        assertEquals(LeaveStatus.REJECTED, lr.getStatus());
        assertEquals("REJECTED", lr.getStage());
        assertEquals(0.0, balanceOf(requester).getPendingDays());
        assertEquals(0.0, balanceOf(requester).getUsedDays());
    }

    // ----- the only General Manager applies (HR_ADMIN override of a role step) --------------------------------

    /** The GM's own leave, forwarded by the branch manager and the secretary and approved by the head: at GM. */
    private long gmLeaveAtGm() {
        balance(gm, 21.0);
        as(gm, "GENERAL_MANAGER");
        long id = leave.applyLeave(gm, CreateLeaveRequest.builder().leaveTypeCode(TYPE)
            .startDate(monday).endDate(monday.plusDays(1)).reason("Own leave").build(), null).getLeaveReqId();
        act(id, branchManager, "EMPLOYEE", "BRANCH", StepOutcome.FORWARD, dept, null);
        act(id, secretary, "EMPLOYEE", "SECRETARY", StepOutcome.FORWARD, null, null);
        act(id, head, "EMPLOYEE", "HEAD", StepOutcome.APPROVE, null, null);
        assertEquals("PENDING_GM", request(id).getStage());
        return id;
    }

    @Test
    void theSoleGeneralManagersOwnLeaveIsOverriddenByAnHrAdminAndGoesOnToHr() {
        when(guard.isSoleActiveHolderOf("GENERAL_MANAGER", gm)).thenReturn(true);
        long id = gmLeaveAtGm();

        as(gm, "GENERAL_MANAGER");
        assertEquals("SELF_APPROVAL", assertThrows(BusinessRuleException.class, () -> workflow.act(RequestType.LEAVE,
            id, new WorkflowActionRequest("GM", StepOutcome.APPROVE, null, null))).getRuleCode());

        as(hr, "HR_ADMIN");
        assertTrue(workflow.status(RequestType.LEAVE, id).mayOverride());
        assertEquals("PENDING_HR", workflow.override(RequestType.LEAVE, id,
            new WorkflowOverrideRequest("GM", StepOutcome.APPROVE, null, "Only general manager")).stage());
        var last = workflow.history(RequestType.LEAVE, id).getLast();
        assertEquals(com.hrms.workflow.enums.HistoryAction.OVERRIDDEN, last.action());
        assertEquals("GM", last.stepKey());
        assertEquals("PENDING_HR", request(id).getStage());
    }

    @Test
    void withASecondGeneralManagerTheGmStepIsNotOverridden() {
        when(guard.isSoleActiveHolderOf("GENERAL_MANAGER", gm)).thenReturn(false);
        long id = gmLeaveAtGm();

        as(hr, "HR_ADMIN");
        assertFalse(workflow.status(RequestType.LEAVE, id).mayOverride());
        assertThrows(AccessDeniedException.class, () -> workflow.override(RequestType.LEAVE, id,
            new WorkflowOverrideRequest("GM", StepOutcome.APPROVE, null, "Only general manager")));
        assertEquals("PENDING_GM", request(id).getStage());
    }

    /** Documented limit: the only HR_ADMIN cannot push their own leave through the HR step; another HR_ADMIN must. */
    @Test
    void anHrAdminCannotOverrideTheirOwnRequestEvenAsTheOnlyHolder() {
        when(guard.isSoleActiveHolderOf("HR_ADMIN", hr)).thenReturn(true);
        balance(hr, 21.0);
        as(hr, "HR_ADMIN");
        long id = leave.applyLeave(hr, CreateLeaveRequest.builder().leaveTypeCode(TYPE)
            .startDate(monday).endDate(monday.plusDays(1)).reason("Own leave").build(), null).getLeaveReqId();
        toHr(id);

        as(hr, "HR_ADMIN");
        assertFalse(workflow.status(RequestType.LEAVE, id).mayOverride());
        assertEquals("SELF_APPROVAL", assertThrows(BusinessRuleException.class, () -> workflow.override(
            RequestType.LEAVE, id, new WorkflowOverrideRequest("HR", StepOutcome.APPROVE, null, "Own"))).getRuleCode());
    }

    @Test
    void cancellingAfterTheGmApprovedButBeforeHrReleasesThePendingDays() {
        LeaveRequestResponse r = apply();
        long id = r.getLeaveReqId();
        toHr(id);

        as(requester, "EMPLOYEE");
        leave.cancelLeave(id, requester);

        LeaveRequest lr = request(id);
        assertEquals(LeaveStatus.CANCELLED, lr.getStatus());
        assertEquals("CANCELLED", lr.getStage());
        assertEquals(0.0, balanceOf(requester).getPendingDays());
        assertEquals(0.0, balanceOf(requester).getUsedDays());
        assertEquals(0, taskService.createTaskQuery().processInstanceId(lr.getProcessInstanceId()).count());
    }

    // ----- a failing handler ----------------------------------------------------------------------------------

    /**
     * HR processes but the balance row is gone, so the completion handler fails: the whole action rolls back, the
     * request is still PENDING at HR, the days are unchanged and the HR task is still open.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aHandlerFailureOnTheHrStepLeavesTheRequestPendingAndTheBalanceUntouched() {
        long[] people = tx.execute(s -> {
            seed();
            return new long[]{requester, branchManager, secretary, head, gm, hr};
        });
        WorkflowDefinition def = tx.execute(s -> {
            enable(true);
            return definitions.publishDefault(RequestType.LEAVE);
        });
        long id = 0;
        try {
            LeaveRequestResponse r = tx.execute(s -> apply());
            id = r.getLeaveReqId();
            final long requestId = id;
            tx.executeWithoutResult(s -> toHr(requestId));
            tx.executeWithoutResult(s -> jdbc.update("delete from LEAVE_BALANCES where EMPLOYEE_ID = ?", requester));

            assertThrows(ResourceNotFoundException.class, () -> tx.executeWithoutResult(s ->
                act(requestId, hr, "HR_ADMIN", "HR", StepOutcome.APPROVE, null, null)));

            tx.executeWithoutResult(s -> {
                LeaveRequest lr = request(requestId);
                assertEquals(LeaveStatus.PENDING, lr.getStatus());
                assertEquals("PENDING_HR", lr.getStage());
                assertEquals("HR", taskService.createTaskQuery().processInstanceId(lr.getProcessInstanceId())
                    .singleResult().getTaskDefinitionKey());
                assertEquals(5L, jdbc.queryForObject("select count(*) from REQUEST_HISTORY where REQUEST_TYPE = 'LEAVE'"
                    + " and REQUEST_ID = ?", Long.class, requestId));
            });
        } finally {
            cleanUp(def, people, id);
        }
    }

    /**
     * Real leave port and real inbox query: the head changes in a committed transaction and the refresher re-points
     * the request waiting at the HEAD step, so the new head finds it in the inbox and the old head no longer does.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aNewDepartmentHeadIsStoredOnTheWaitingLeaveAndSeesItInTheRealInbox() {
        long[] people = tx.execute(s -> {
            seed();
            return new long[]{requester, branchManager, secretary, head, gm, hr};
        });
        WorkflowDefinition def = tx.execute(s -> {
            enable(true);
            return definitions.publishDefault(RequestType.LEAVE);
        });
        long id = 0;
        try {
            id = tx.execute(s -> apply()).getLeaveReqId();
            final long requestId = id;
            tx.executeWithoutResult(s -> {
                act(requestId, branchManager, "EMPLOYEE", "BRANCH", StepOutcome.FORWARD, dept, null);
                act(requestId, secretary, "EMPLOYEE", "SECRETARY", StepOutcome.FORWARD, null, null);
            });
            assertEquals(head, tx.execute(s -> request(requestId)).getCurrentApproverEmployeeId());
            assertTrue(inboxIds(head).contains(requestId));
            assertFalse(inboxIds(gm).contains(requestId));

            when(organisation.headOf(dept)).thenReturn(Optional.of(gm));
            tx.executeWithoutResult(s -> events.publishEvent(new com.hrms.employee.service.OrganisationChanged()));

            LeaveRequest stored = tx.execute(s -> request(requestId));
            assertEquals("PENDING_HEAD", stored.getStage());
            assertEquals(gm, stored.getCurrentApproverEmployeeId());
            assertTrue(inboxIds(gm).contains(requestId));
            assertFalse(inboxIds(head).contains(requestId));
        } finally {
            cleanUp(def, people, id);
        }
    }

    private List<Long> inboxIds(long who) {
        as(who, "EMPLOYEE");
        return tx.execute(s -> inbox.inbox(0, 100).getContent().stream().map(i -> i.requestId()).toList());
    }

    private void cleanUp(WorkflowDefinition def, long[] people, long requestId) {
        SecurityContextHolder.clearContext();
        tx.executeWithoutResult(s -> {
            if (requestId != 0) {
                jdbc.update("delete from REQUEST_HISTORY where REQUEST_TYPE = 'LEAVE' and REQUEST_ID = ?", requestId);
                jdbc.update("delete from AUDIT_EVENTS where TARGET_ID = ? and TARGET_TYPE in ('LEAVE', 'LEAVE_REQUEST')",
                    String.valueOf(requestId));
                jdbc.update("delete from LEAVE_REQUESTS where LEAVE_REQ_ID = ?", requestId);
            }
            jdbc.update("delete from LEAVE_BALANCES where EMPLOYEE_ID = ?", people[0]);
            jdbc.update("delete from DEPARTMENTS where DEPT_ID = ?", dept);
            if (createdTypeId != null) {
                jdbc.update("delete from LEAVE_TYPES where LEAVE_TYPE_ID = ?", createdTypeId);
            }
            long defId = def.getDefinitionId();
            jdbc.update("delete from WORKFLOW_ROUTES where STEP_ID in (select STEP_ID from WORKFLOW_STEPS"
                + " where DEFINITION_ID = ?)", defId);
            jdbc.update("delete from WORKFLOW_STEPS where DEFINITION_ID = ?", defId);
            jdbc.update("delete from AUDIT_EVENTS where ACTION = 'WORKFLOW_PUBLISHED' and TARGET_ID = ?",
                String.valueOf(defId));
            jdbc.update("delete from WORKFLOW_DEFINITIONS where DEFINITION_ID = ?", defId);
            jdbc.update("update WORKFLOW_SETTINGS set SETTING_VALUE = 'false' where SETTING_KEY = 'WORKFLOW_ENABLED_LEAVE'");
            for (long p : people) {
                jdbc.update("delete from EMPLOYEES where EMPLOYEE_ID = ?", p);
            }
        });
        repositoryService.deleteDeployment(def.getFlowableDeploymentId(), true);
    }
}
