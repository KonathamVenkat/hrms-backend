package com.hrms.employee;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.dto.PagedResponse;
import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.recruitment.dto.request.ApproveRequisitionRequest;
import com.hrms.recruitment.dto.request.RejectRequisitionRequest;
import com.hrms.recruitment.dto.request.RequisitionActionRequest;
import com.hrms.recruitment.dto.request.RequisitionRequest;
import com.hrms.recruitment.dto.request.StageRequest;
import com.hrms.recruitment.dto.response.RequisitionResponse;
import com.hrms.recruitment.dto.response.RequisitionSummaryResponse;
import com.hrms.recruitment.dto.response.StageResponse;
import com.hrms.recruitment.enums.ApprovalDecision;
import com.hrms.recruitment.enums.RequisitionPriority;
import com.hrms.recruitment.enums.RequisitionStatus;
import com.hrms.recruitment.enums.RequisitionType;
import com.hrms.recruitment.enums.StageType;
import com.hrms.recruitment.service.RequisitionService;
import com.hrms.recruitment.service.StageService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * The requisition flow and the stage rules end to end against a real Oracle schema built from db/migration (V1..V7):
 * the entities match the columns, the approval row and the version counter really persist, and the visibility rules
 * hold with real data. Only the signed-in user is faked. Each step ends with flush and clear, because a real request
 * starts with an empty persistence context.
 */
@Transactional
class RecruitmentRequisitionDbTest extends DatabaseTest {

    static final long DEPT = 990001L;
    static final long DESIG = 990001L;

    @Autowired RequisitionService requisitions;
    @Autowired StageService stages;
    @Autowired EmployeeRepository employees;
    @Autowired JdbcTemplate jdbc;
    @PersistenceContext EntityManager em;
    @MockitoBean EmployeeAccessGuard guard;

    Long head;
    Long hr;
    Long stranger;

    @BeforeEach
    void data() {
        jdbc.update("insert into HRMS.DEPARTMENTS (DEPT_ID, CODE, NAME, NAME_AR) values (?, 'RECT', 'Rec test', N'اختبار')", DEPT);
        jdbc.update("insert into HRMS.DESIGNATIONS (DESIG_ID, TITLE, CODE, DEPARTMENT_ID) values (?, 'Analyst', 'RECT_AN', ?)", DESIG, DEPT);
        head = employee("head");
        hr = employee("hr");
        stranger = employee("stranger");
        jdbc.update("update HRMS.DEPARTMENTS set HEAD_EMPLOYEE_ID = ? where DEPT_ID = ?", head, DEPT);
        fresh();
    }

    private void fresh() {
        em.flush();
        em.clear();
    }

    private Long employee(String tag) {
        return employees.saveAndFlush(Employee.builder()
            .firstName("Test").firstNameAr("ع").lastName(tag).lastNameAr("ل")
            .dateOfBirth(LocalDate.of(1988, 3, 2)).gender(Gender.MALE)
            .personalEmail("rec-" + tag + "-p@example.test").workEmail("rec-" + tag + "-w@example.test")
            .hireDate(LocalDate.of(2024, 1, 15)).employmentType(EmploymentType.FULL_TIME).build()).getId();
    }

    private void signInAs(Long employeeId, boolean hrStaff) {
        when(guard.currentEmployeeId()).thenReturn(employeeId);
        when(guard.isHrStaff()).thenReturn(hrStaff);
    }

    private RequisitionRequest request(Long version) {
        return new RequisitionRequest("Data Analyst", "محلل بيانات", DEPT, DESIG, null, null, 2, EmploymentType.FULL_TIME,
            RequisitionType.NEW, null, new BigDecimal("2.0"), new BigDecimal("5.0"), new BigDecimal("500.000"),
            new BigDecimal("900.500"), RequisitionPriority.HIGH, LocalDate.now().plusDays(30), "Team is growing", version);
    }

    private RequisitionResponse raised() {
        signInAs(head, false);
        RequisitionResponse r = requisitions.create(request(null));
        fresh();
        return r;
    }

    // ------------------------------------------------------------------------------------ the flow

    @Test
    void aDepartmentHeadRaisesIt_hrApprovesIt_andEverythingIsStoredWithItsNames() {
        RequisitionResponse draft = raised();
        assertEquals(RequisitionStatus.DRAFT, draft.status());
        assertTrue(draft.code().matches("REQ-\\d{4}-\\d{6}"), draft.code());
        assertEquals("SSP", draft.currency());
        assertEquals(head, draft.requestedById());
        assertEquals("Rec test", draft.departmentName());
        assertEquals("Analyst", draft.designationTitle());
        assertEquals("محلل بيانات", draft.jobTitleAr());
        assertEquals(0, new BigDecimal("900.500").compareTo(draft.maxSalary()));
        assertNotNull(draft.createdAt());
        assertEquals(0L, draft.version());

        RequisitionResponse submitted = requisitions.submit(draft.id(), new RequisitionActionRequest(draft.version()));
        fresh();
        assertEquals(RequisitionStatus.PENDING_APPROVAL, submitted.status());
        assertEquals(1L, submitted.version());

        signInAs(hr, true);
        RequisitionResponse approved = requisitions.approve(draft.id(),
            new ApproveRequisitionRequest(true, true, "Budget line 4", submitted.version()));
        fresh();
        assertEquals(RequisitionStatus.APPROVED, approved.status());

        RequisitionResponse reread = requisitions.get(draft.id());
        assertEquals(1, reread.approvals().size());
        assertEquals(ApprovalDecision.APPROVED, reread.approvals().get(0).decision());
        assertEquals(hr, reread.approvals().get(0).approverId());
        assertTrue(reread.approvals().get(0).budgetConfirmed());
        assertEquals("Budget line 4", reread.approvals().get(0).comments());
    }

    @Test
    void aSecondApprovalAndAStaleScreenAreBothRefused() {
        RequisitionResponse draft = raised();
        requisitions.submit(draft.id(), new RequisitionActionRequest(0L));
        fresh();
        signInAs(hr, true);

        assertThrows(OptimisticLockingFailureException.class, () ->
            requisitions.approve(draft.id(), new ApproveRequisitionRequest(true, true, null, 0L))); // submit moved it to 1
        fresh();

        requisitions.approve(draft.id(), new ApproveRequisitionRequest(true, true, null, 1L));
        fresh();
        BusinessRuleException again = assertThrows(BusinessRuleException.class, () ->
            requisitions.approve(draft.id(), new ApproveRequisitionRequest(true, true, null, 2L)));
        assertEquals("REQUISITION_INVALID_STATE", again.getRuleCode());
        assertEquals(1L, jdbc.queryForObject(
            "select count(*) from HRMS.REQUISITION_APPROVALS where REQUISITION_ID = ?", Long.class, draft.id()));
    }

    @Test
    void aRejectionKeepsItsReasonAndCannotBeReopened() {
        RequisitionResponse draft = raised();
        requisitions.submit(draft.id(), new RequisitionActionRequest(0L));
        fresh();
        signInAs(hr, true);

        RequisitionResponse rejected = requisitions.reject(draft.id(), new RejectRequisitionRequest("No budget this year", 1L));
        fresh();
        assertEquals(RequisitionStatus.REJECTED, rejected.status());
        assertEquals("No budget this year", requisitions.get(draft.id()).approvals().get(0).comments());
        assertThrows(BusinessRuleException.class, () -> requisitions.submit(draft.id(), new RequisitionActionRequest(2L)));
    }

    @Test
    void onlyADraftCanBeEdited_andAnEditBumpsTheVersion() {
        RequisitionResponse draft = raised();
        RequisitionRequest changed = request(draft.version());
        RequisitionResponse edited = requisitions.update(draft.id(), new RequisitionRequest("Senior Data Analyst",
            changed.jobTitleAr(), changed.departmentId(), changed.designationId(), null, null, changed.noOfPositions(),
            changed.employmentType(), changed.requisitionType(), null, changed.minExperienceYears(), changed.maxExperienceYears(),
            changed.minSalary(), changed.maxSalary(), changed.priority(), changed.targetJoinDate(), changed.justification(),
            changed.version()));
        fresh();
        assertEquals("Senior Data Analyst", edited.jobTitle());
        assertEquals(1L, edited.version());
        assertEquals(1L, jdbc.queryForObject("select VERSION from HRMS.JOB_REQUISITIONS where REQUISITION_ID = ?", Long.class, draft.id()));

        requisitions.submit(draft.id(), new RequisitionActionRequest(1L));
        fresh();
        assertThrows(BusinessRuleException.class, () -> requisitions.update(draft.id(), request(2L)));
    }

    @Test
    void closingStampsTheClosedTime() {
        RequisitionResponse draft = raised();
        requisitions.submit(draft.id(), new RequisitionActionRequest(0L));
        fresh();
        signInAs(hr, true);
        requisitions.approve(draft.id(), new ApproveRequisitionRequest(true, true, null, 1L));
        fresh();
        RequisitionResponse closed = requisitions.close(draft.id(), new RequisitionActionRequest(2L));
        fresh();
        assertEquals(RequisitionStatus.CLOSED, closed.status());
        assertNotNull(closed.closedAt());
    }

    // ------------------------------------------------------------------------------ who sees what

    @Test
    void theListShowsEachCallerOnlyWhatTheyMaySee() {
        raised();

        signInAs(head, false);
        when(guard.currentEmployeeId()).thenReturn(head);
        assertEquals(1, search().getTotalElements());

        signInAs(stranger, false);
        assertEquals(0, search().getTotalElements());

        signInAs(hr, true);
        PagedResponse<RequisitionSummaryResponse> all = search();
        assertEquals(1, all.getTotalElements());
        assertEquals("Rec test", all.getContent().get(0).departmentName());
        assertEquals("Test head", all.getContent().get(0).requestedByName());
    }

    @Test
    void theListFiltersByStatusAndText() {
        raised();
        signInAs(hr, true);
        assertEquals(1, requisitions.search(RequisitionStatus.DRAFT, DEPT, "data ana", 0, 20, "jobTitle", "asc").getTotalElements());
        assertEquals(0, requisitions.search(RequisitionStatus.APPROVED, null, null, 0, 20, "createdAt", "desc").getTotalElements());
        assertEquals(0, requisitions.search(null, null, "100%_", 0, 20, "createdAt", "desc").getTotalElements()); // wildcards are literal
    }

    private PagedResponse<RequisitionSummaryResponse> search() {
        return requisitions.search(null, null, null, 0, 20, "createdAt", "desc");
    }

    // ------------------------------------------------------------------------------------- stages

    private StageRequest stage(String code, StageType type, Boolean active, Long version) {
        return new StageRequest(code, "Name " + code, "اسم", type, 9, active, version);
    }

    @Test
    void aSecondActiveTerminalStageIsRefusedButAnInactiveOneIsFine() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> stages.create(stage("HIRED_2", StageType.HIRED, true, null)));
        assertEquals("STAGE_TERMINAL_EXISTS", ex.getRuleCode());
        StageResponse inactive = stages.create(stage("HIRED_OLD", StageType.HIRED, false, null));
        assertFalse(inactive.isActive());
        assertFalse(inactive.isSystem());
        assertNotNull(inactive.createdAt());
        fresh();
        assertThrows(BusinessRuleException.class, () -> stages.activate(inactive.id()));
    }

    @Test
    void systemStagesAreProtected_andATypeNeverChanges() {
        StageResponse applied = stages.getAll().stream().filter(s -> s.code().equals("APPLIED")).findFirst().orElseThrow();
        assertTrue(applied.isSystem());
        assertEquals("STAGE_SYSTEM_PROTECTED", assertThrows(BusinessRuleException.class, () -> stages.delete(applied.id())).getRuleCode());
        assertEquals("STAGE_SYSTEM_PROTECTED", assertThrows(BusinessRuleException.class, () -> stages.deactivate(applied.id())).getRuleCode());
        assertEquals("STAGE_SYSTEM_PROTECTED", assertThrows(BusinessRuleException.class,
            () -> stages.update(applied.id(), stage("RENAMED", StageType.OPEN, true, applied.version()))).getRuleCode());
        assertEquals("STAGE_TYPE_LOCKED", assertThrows(BusinessRuleException.class,
            () -> stages.update(applied.id(), stage("APPLIED", StageType.REJECTED, true, applied.version()))).getRuleCode());

        StageResponse renamed = stages.update(applied.id(), stage("APPLIED", StageType.OPEN, true, applied.version()));
        fresh();
        assertEquals("Name APPLIED", renamed.name()); // name and order may change, the code may not
        assertEquals(applied.version() + 1, renamed.version());
    }

    @Test
    void anOrdinaryStageCanBeCreatedEditedAndDeleted_andAStaleEditIsRefused() {
        StageResponse created = stages.create(stage("PHONE_SCREEN", StageType.OPEN, null, null));
        fresh();
        StageResponse edited = stages.update(created.id(), stage("PHONE_SCREEN", StageType.OPEN, true, created.version()));
        fresh();
        assertThrows(OptimisticLockingFailureException.class,
            () -> stages.update(created.id(), stage("PHONE_SCREEN", StageType.OPEN, true, created.version())));
        stages.delete(edited.id());
        fresh();
        assertTrue(stages.getAll().stream().noneMatch(s -> s.code().equals("PHONE_SCREEN")));
    }
}
