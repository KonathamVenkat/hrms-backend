package com.hrms.workflow;

import com.hrms.auth.entity.AuthUser;
import com.hrms.auth.repository.AuthUserRepository;
import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import com.hrms.common.enums.UserRole;
import com.hrms.common.security.JwtTokenProvider;
import com.hrms.employee.DatabaseTest;
import com.hrms.employee.EmployeeApplication;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.leave.entity.LeaveBalance;
import com.hrms.leave.entity.LeaveRequest;
import com.hrms.leave.entity.LeaveStatus;
import com.hrms.leave.entity.LeaveType;
import com.hrms.leave.repository.LeaveBalanceRepository;
import com.hrms.leave.repository.LeaveRequestRepository;
import com.hrms.leave.repository.LeaveTypeRepository;
import com.hrms.workflow.entity.WorkflowSetting;
import com.hrms.workflow.enums.RequestType;
import com.hrms.workflow.repository.WorkflowSettingRepository;
import com.hrms.workflow.service.WorkflowDefinitionService;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole leave route through the real security chain: a real JWT per person, the real organisation lookup (branch
 * manager, department head and secretary come from rows), the real leave service and Flowable. Complements the service
 * level tests (WorkflowActionsDbTest, LeaveWorkflowDbTest) with the HTTP chain: who sees what at each stage, 403 for
 * the wrong person, 409 for a repeated act, the history, and the leave balance. Everything is rolled back.
 */
@SpringBootTest(classes = EmployeeApplication.class)
@Transactional
class LeaveFlowEndToEndDbTest extends DatabaseTest {

    private static final String TYPE = "ANNUAL";
    private static final long ID = 990301L;
    private static final String BASE = "/api/v1/requests/LEAVE/";

    @Autowired WebApplicationContext context;
    @Autowired JwtTokenProvider jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired EmployeeRepository employees;
    @Autowired AuthUserRepository authUsers;
    @Autowired LeaveTypeRepository types;
    @Autowired LeaveBalanceRepository balances;
    @Autowired LeaveRequestRepository requests;
    @Autowired WorkflowSettingRepository settings;
    @Autowired WorkflowDefinitionService definitions;
    @Autowired com.hrms.employee.service.DepartmentHeadService departmentHeads;

    MockMvc mvc;
    long requester, branchManager, secretary, head, gm, hr, other;
    private String run;
    private LocalDate monday;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
        run = Long.toString(System.nanoTime(), 36);
        monday = LocalDate.now().plusDays(1);
        while (monday.getDayOfWeek() != DayOfWeek.MONDAY) {
            monday = monday.plusDays(1);
        }
        requester = person("req", UserRole.EMPLOYEE);
        branchManager = person("bm", UserRole.EMPLOYEE);
        secretary = person("sec", UserRole.EMPLOYEE);
        head = person("head", UserRole.EMPLOYEE);
        gm = person("gm", UserRole.GENERAL_MANAGER);
        hr = person("hr", UserRole.HR_ADMIN);
        other = person("oth", UserRole.EMPLOYEE);
        organisation();
        if (types.findByCodeIgnoreCase(TYPE).isEmpty()) {
            types.saveAndFlush(LeaveType.builder().code(TYPE).nameEn("E2e annual " + run).nameAr("ع").createdBy("test").build());
        }
        balances.saveAndFlush(LeaveBalance.builder().employeeId(requester).leaveTypeCode(TYPE)
            .year(monday.getYear()).totalDays(21.0).build());
        definitions.publishDefault(RequestType.LEAVE);
        WorkflowSetting flag = settings.findById("WORKFLOW_ENABLED_LEAVE").orElseThrow();
        flag.setSettingValue("true");
        settings.saveAndFlush(flag);
    }

    // ----- fixtures -------------------------------------------------------------------------------------------

    private long person(String tag, UserRole role) {
        long id = employees.saveAndFlush(Employee.builder()
            .employeeCode("E2E-" + tag + "-" + run).firstName("E2e").firstNameAr("ع").lastName(tag).lastNameAr("ل")
            .dateOfBirth(LocalDate.of(1990, 1, 1)).gender(Gender.MALE)
            .personalEmail("e2e-" + tag + "-" + run + "-p@example.test")
            .workEmail("e2e-" + tag + "-" + run + "-w@example.test")
            .hireDate(LocalDate.of(2024, 1, 1)).employmentType(EmploymentType.FULL_TIME).build()).getId();
        authUsers.saveAndFlush(AuthUser.builder().username("e2e-" + tag + "-" + run).email("e2e-" + tag + "-" + run + "@example.test")
            .passwordHash("not-a-real-hash").role(role).employeeId(id).fullNameEn("E2e " + tag).isActive(true)
            .isLocked(false).failedAttempts(0).mustChangePassword(0).build());
        return id;
    }

    /** One branch managed by BM, one department with a secretary and a head; every person works in that branch. */
    private void organisation() {
        jdbc.update("""
            insert into HRMS.DEPARTMENTS (DEPT_ID, CODE, NAME, NAME_AR, HEAD_EMPLOYEE_ID, SECRETARY_EMPLOYEE_ID, IS_ACTIVE)
            values (?, 'E2ET', 'E2e dept', N'x', ?, ?, 1)""", ID, head, secretary);
        jdbc.update("insert into HRMS.DESIGNATIONS (DESIG_ID, TITLE, CODE, DEPARTMENT_ID) values (?, 'Clerk', 'E2ET_CL', ?)", ID, ID);
        jdbc.update("""
            insert into HRMS.OFFICE_LOCATIONS (LOCATION_ID, LOCATION_CODE, LOCATION_NAME, LOCATION_NAME_AR, ADDRESS_LINE1, CITY)
            values (?, 'E2ET', 'E2e HQ', N'x', 'Road 1', 'Juba')""", ID);
        jdbc.update("insert into HRMS.COMPANIES (COMPANY_ID, COMPANIES_CODE, COMPANIES_NAME, COMPANIES_NAME_AR) values (?, 'E2ET', 'E2e', N'x')", ID);
        jdbc.update("""
            insert into HRMS.COMPANY_BRANCHES (COMPANY_BRANCH_ID, COMPANY_ID, COMPANY_BRANCHES_CODE, COMPANY_BRANCHES_NAME,
                COMPANY_BRANCHES_NAME_AR, LOCATION_ID, BRANCH_MANAGER_EMPLOYEE_ID)
            values (?, ?, 'E2ET_B1', 'E2e Branch', N'x', ?, ?)""", ID, ID, ID, branchManager);
        for (long e : List.of(requester, secretary, head, gm, hr, other, branchManager)) {
            jdbc.update("""
                insert into HRMS.EMPLOYEE_JOB_DETAILS (JOB_DETAILS_ID, EMPLOYEE_ID, DEPARTMENT_ID, DESIGNATION_ID, LOCATION_ID,
                    COMPANY_BRANCH_ID, WORK_MODE, EFFECTIVE_FROM, IS_CURRENT)
                values (HRMS.SEQ_EMPLOYEE_JOB_DETAILS.NEXTVAL, ?, ?, ?, ?, ?, 'ON_SITE', DATE '2024-01-15', 1)""",
                e, ID, ID, ID, ID);
        }
        em.clear();
    }

    // ----- HTTP helpers ---------------------------------------------------------------------------------------

    private String token(long employeeId, String... roles) {
        String name = authUsers.findByEmployeeId(employeeId).orElseThrow().getUsername();
        return jwt.generateAccessToken(name, name + "@example.test", List.of(roles), employeeId);
    }

    /** Signed in as the given person, with the role they would hold. */
    private String tokenOf(long who) {
        if (who == hr) {
            return token(who, "HR_ADMIN");
        }
        if (who == gm) {
            return token(who, "GENERAL_MANAGER");
        }
        return token(who, "EMPLOYEE");
    }

    private ResultActions call(long who, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + tokenOf(who)));
    }

    private String body(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    private long apply() throws Exception {
        return apply(requester);
    }

    private long apply(long who) throws Exception {
        String json = "{\"leaveTypeCode\":\"" + TYPE + "\",\"startDate\":\"" + monday + "\",\"endDate\":\""
            + monday.plusDays(4) + "\",\"reason\":\"Family visit\"}";
        String created = body(call(who, multipart("/api/v1/employees/" + who + "/leave-requests")
            .file(new MockMultipartFile("request", "", "application/json", json.getBytes())))
            .andExpect(status().isCreated()));
        return ((Number) JsonPath.read(created, "$.data.leaveReqId")).longValue();
    }

    private String json(String step, String outcome, Long department, String comment) {
        return "{\"stepKey\":\"" + step + "\",\"outcome\":\"" + outcome + "\""
            + (department == null ? "" : ",\"targetDepartmentId\":" + department)
            + (comment == null ? "" : ",\"comment\":\"" + comment + "\"") + "}";
    }

    private ResultActions actAs(long who, long id, String json) throws Exception {
        return call(who, post(BASE + id + "/actions").contentType("application/json").content(json));
    }

    /** The right person acts; then the same act again is a 409 and the wrong person is a 403 (checked first). */
    private void actOk(long id, long who, String step, String outcome, Long department, String comment, String nextStage)
            throws Exception {
        String json = json(step, outcome, department, comment);
        actAs(other, id, json).andExpect(status().isForbidden());
        String result = body(actAs(who, id, json).andExpect(status().isOk()));
        assertEquals(nextStage, JsonPath.read(result, "$.data.stage"));
        actAs(who, id, json).andExpect(status().isConflict());
    }

    private String stageSeenBy(long who, long id) throws Exception {
        return JsonPath.read(body(call(who, get(BASE + id + "/workflow")).andExpect(status().isOk())), "$.data.stage");
    }

    /** Request ids in the caller's inbox; a role inbox may hold other rows, so tests check containment only. */
    private List<Number> inboxOf(long who) throws Exception {
        return JsonPath.read(body(call(who, get("/api/v1/inbox?size=100")).andExpect(status().isOk())),
            "$.data.content[*].requestId");
    }

    private void assertInboxOnlyFor(long id, long... holders) throws Exception {
        for (long who : List.of(requester, branchManager, secretary, head, gm, hr, other).stream()
                .mapToLong(Long::longValue).toArray()) {
            boolean expected = java.util.Arrays.stream(holders).anyMatch(h -> h == who);
            boolean seen = inboxOf(who).stream().anyMatch(n -> n.longValue() == id);
            assertEquals(expected, seen, "inbox of " + who + " at " + id);
        }
    }

    private LeaveBalance balance() {
        em.flush();
        em.clear();
        return balances.findByEmployeeIdAndLeaveTypeCodeAndYear(requester, TYPE, monday.getYear()).orElseThrow();
    }

    private LeaveRequest leave(long id) {
        em.flush();
        em.clear();
        return requests.findById(id).orElseThrow();
    }

    private List<String> history(long id, String path) throws Exception {
        return JsonPath.read(body(call(hr, get(BASE + id + "/history")).andExpect(status().isOk())),
            "$.data[*]." + path);
    }

    // ----- the chain ------------------------------------------------------------------------------------------

    @Test
    void employeeToBranchToSecretaryToHeadToGmToHrEndsApprovedAndTheDaysMoveOnlyAtHr() throws Exception {
        long id = apply();
        assertEquals("PENDING_BRANCH", stageSeenBy(requester, id));
        double days = balance().getPendingDays();
        assertTrue(days > 0);
        assertEquals(0.0, balance().getUsedDays());
        assertInboxOnlyFor(id, branchManager);
        assertEquals("PENDING_BRANCH", stageSeenBy(requester, id));
        assertTrue(body(call(branchManager, get(BASE + id + "/workflow"))).contains("\"pickTargetDepartment\":true"));
        assertTrue(body(call(requester, get(BASE + id + "/workflow"))).contains("\"allowedOutcomes\":[]"));
        call(other, get(BASE + id + "/workflow")).andExpect(status().isForbidden());

        // the branch manager picks the department from the list the server offers him
        assertTrue(body(call(branchManager, get(BASE + id + "/workflow/target-departments"))
            .andExpect(status().isOk())).contains("\"id\":" + ID));
        actOk(id, branchManager, "BRANCH", "FORWARD", ID, null, "PENDING_SECRETARY");
        assertEquals(ID, leave(id).getTargetDepartmentId());
        assertInboxOnlyFor(id, secretary);

        actOk(id, secretary, "SECRETARY", "FORWARD", null, null, "PENDING_HEAD");
        assertInboxOnlyFor(id, head);
        actAs(secretary, id, json("HEAD", "APPROVE", null, null)).andExpect(status().isForbidden());

        actOk(id, head, "HEAD", "APPROVE", null, null, "PENDING_GM");
        assertInboxOnlyFor(id, gm);
        actAs(head, id, json("GM", "APPROVE", null, null)).andExpect(status().isForbidden());

        actOk(id, gm, "GM", "APPROVE", null, "ok", "PENDING_HR");
        assertInboxOnlyFor(id, hr);
        actAs(gm, id, json("HR", "APPROVE", null, null)).andExpect(status().isForbidden());
        assertEquals(days, balance().getPendingDays());
        assertEquals(0.0, balance().getUsedDays());
        assertEquals(LeaveStatus.PENDING, leave(id).getStatus());

        actOk(id, hr, "HR", "APPROVE", null, "processed", "APPROVED");
        assertInboxOnlyFor(id);
        assertEquals("APPROVED", stageSeenBy(requester, id));
        assertEquals(LeaveStatus.APPROVED, leave(id).getStatus());
        assertEquals("APPROVED", leave(id).getStage());
        assertEquals(0.0, balance().getPendingDays());
        assertEquals(days, balance().getUsedDays());

        assertEquals(List.of("SUBMITTED", "FORWARDED", "FORWARDED", "APPROVED", "APPROVED", "APPROVED"), history(id, "action"));
        assertEquals(List.of("BRANCH", "SECRETARY", "HEAD", "GM", "HR"), history(id, "stepKey") /* SUBMITTED has no step */);
        List<Number> actors = JsonPath.read(body(call(hr, get(BASE + id + "/history"))), "$.data[*].actorEmployeeId");
        assertEquals(List.of(requester, branchManager, secretary, head, gm, hr),
            actors.stream().map(Number::longValue).toList());
        List<String> roles = history(id, "actorRole");
        assertEquals("EMPLOYEE", roles.get(0));
        assertEquals("GENERAL_MANAGER", roles.get(4));
        assertEquals("HR_ADMIN", roles.get(5));
    }

    @Test
    void aRejectionByTheHeadReleasesThePendingDaysAndEndsTheFlow() throws Exception {
        long id = apply();
        double days = balance().getPendingDays();
        actOk(id, branchManager, "BRANCH", "FORWARD", ID, null, "PENDING_SECRETARY");
        actOk(id, secretary, "SECRETARY", "FORWARD", null, null, "PENDING_HEAD");

        actAs(head, id, json("HEAD", "REJECT", null, "  ")).andExpect(status().isUnprocessableEntity()); // reason required
        actOk(id, head, "HEAD", "REJECT", null, "Busy season", "REJECTED");

        assertInboxOnlyFor(id);
        assertEquals(LeaveStatus.REJECTED, leave(id).getStatus());
        assertEquals("Busy season", leave(id).getRejectionReason());
        assertEquals(0.0, balance().getPendingDays());
        assertEquals(0.0, balance().getUsedDays());
        assertTrue(days > 0);
        assertEquals(List.of("SUBMITTED", "FORWARDED", "FORWARDED", "REJECTED"), history(id, "action"));
        actAs(hr, id, json("HR", "APPROVE", null, null)).andExpect(status().isConflict()); // closed: nobody can act
    }

    @Test
    void theRequesterCancelsAfterTheGmApprovedBeforeHrAndOnlyTheRequesterCan() throws Exception {
        long id = apply();
        actOk(id, branchManager, "BRANCH", "FORWARD", ID, null, "PENDING_SECRETARY");
        actOk(id, secretary, "SECRETARY", "FORWARD", null, null, "PENDING_HEAD");
        actOk(id, head, "HEAD", "APPROVE", null, null, "PENDING_GM");
        actOk(id, gm, "GM", "APPROVE", null, null, "PENDING_HR");
        assertTrue(balance().getPendingDays() > 0);

        call(other, post(BASE + id + "/cancel")).andExpect(status().isForbidden());
        call(hr, post(BASE + id + "/cancel")).andExpect(status().isForbidden());
        call(requester, patch("/api/v1/employees/" + requester + "/leave-requests/" + id + "/cancel"))
            .andExpect(status().isOk());

        assertEquals(LeaveStatus.CANCELLED, leave(id).getStatus());
        assertEquals("CANCELLED", leave(id).getStage());
        assertEquals(0.0, balance().getPendingDays());
        assertEquals(0.0, balance().getUsedDays());
        assertInboxOnlyFor(id);
        assertEquals("CANCELLED", stageSeenBy(requester, id));
        assertEquals("CANCELLED", history(id, "action").getLast());
        actAs(hr, id, json("HR", "APPROVE", null, null)).andExpect(status().isConflict());
        call(requester, post(BASE + id + "/cancel")).andExpect(status().isConflict());
    }

    /** An AUTH_USERS row whose ROLE is GENERAL_MANAGER must load (filter, guard, inbox) with a GENERAL_MANAGER-only token. */
    @Test
    void aGeneralManagerAccountLoadsThroughTheSecurityFiltersWithItsOwnRoleOnly() throws Exception {
        em.clear();
        String name = authUsers.findByEmployeeId(gm).orElseThrow().getUsername();
        String onlyGm = jwt.generateAccessToken(name, name + "@example.test", List.of("GENERAL_MANAGER"), gm);

        mvc.perform(get("/api/v1/inbox").header("Authorization", "Bearer " + onlyGm)).andExpect(status().isOk());
        assertEquals(UserRole.GENERAL_MANAGER, authUsers.findByEmployeeId(gm).orElseThrow().getRole());
    }

    /** C1: a branch manager raising leave defaults to their own department and goes straight to its secretary. */
    @Test
    void aBranchManagerCanApplyAndTheirOwnDepartmentIsUsed() throws Exception {
        balances.saveAndFlush(LeaveBalance.builder().employeeId(branchManager).leaveTypeCode(TYPE)
            .year(monday.getYear()).totalDays(21.0).build());
        long id = apply(branchManager);

        assertEquals("PENDING_SECRETARY", stageSeenBy(branchManager, id));
        assertEquals(ID, leave(id).getTargetDepartmentId());
        assertInboxOnlyFor(id, secretary);
    }

    /** I2: replacing the head mid-flight moves the request to the new head's inbox; the old head is refused. */
    @Test
    void aHeadReplacedMidFlightHandsTheRequestToTheNewHead() throws Exception {
        long id = apply();
        actOk(id, branchManager, "BRANCH", "FORWARD", ID, null, "PENDING_SECRETARY");
        actOk(id, secretary, "SECRETARY", "FORWARD", null, null, "PENDING_HEAD");
        assertInboxOnlyFor(id, head);

        departmentHeads.set(other, true);
        em.flush();
        em.clear();

        assertInboxOnlyFor(id, other);
        actAs(head, id, json("HEAD", "APPROVE", null, null)).andExpect(status().isForbidden());
        String result = body(actAs(other, id, json("HEAD", "APPROVE", null, null)).andExpect(status().isOk()));
        assertEquals("PENDING_GM", JsonPath.read(result, "$.data.stage"));
    }

    /** I1: a head step with nobody waits in the HR_ADMIN inbox (to be overridden), and in nobody else's. */
    @Test
    void aRequestWaitingForAnOverrideIsInTheHrAdminInboxOnly() throws Exception {
        long id = apply();
        actOk(id, branchManager, "BRANCH", "FORWARD", ID, null, "PENDING_SECRETARY");
        actOk(id, secretary, "SECRETARY", "FORWARD", null, null, "PENDING_HEAD");

        departmentHeads.set(head, false); // nobody heads the department any more
        em.flush();
        em.clear();

        assertInboxOnlyFor(id, hr);
        String result = body(call(hr, post(BASE + id + "/override").contentType("application/json")
            .content("{\"stepKey\":\"HEAD\",\"outcome\":\"APPROVE\",\"reason\":\"Head post vacant\"}"))
            .andExpect(status().isOk()));
        assertEquals("PENDING_GM", JsonPath.read(result, "$.data.stage"));
    }
}
