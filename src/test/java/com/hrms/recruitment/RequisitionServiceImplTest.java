package com.hrms.recruitment;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.auth.service.AuditTrail;
import com.hrms.common.enums.EmploymentType;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.recruitment.dto.request.ApproveRequisitionRequest;
import com.hrms.recruitment.dto.request.RejectRequisitionRequest;
import com.hrms.recruitment.dto.request.RequisitionActionRequest;
import com.hrms.recruitment.dto.request.RequisitionRequest;
import com.hrms.recruitment.dto.response.RequisitionResponse;
import com.hrms.employee.entity.Department;
import com.hrms.employee.entity.Designation;
import com.hrms.employee.entity.Employee;
import com.hrms.recruitment.dto.response.PersonOptionResponse;
import com.hrms.recruitment.dto.response.RaiseOptionsResponse;
import com.hrms.recruitment.entity.JobRequisition;
import com.hrms.recruitment.entity.RequisitionApproval;
import com.hrms.recruitment.enums.ApprovalDecision;
import com.hrms.recruitment.enums.RequisitionPriority;
import com.hrms.recruitment.enums.RequisitionStatus;
import com.hrms.recruitment.enums.RequisitionType;
import com.hrms.recruitment.repository.JobRequisitionRepository;
import com.hrms.recruitment.repository.RequisitionApprovalRepository;
import com.hrms.recruitment.service.RecruitmentOrgLookup;
import com.hrms.recruitment.service.impl.RequisitionServiceImpl;
import com.hrms.recruitment.workflow.RequisitionWorkflow;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.hrms.recruitment.enums.RequisitionStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The rules of the requisition service with the repositories mocked: who may do what, and which state moves are legal. */
@ExtendWith(MockitoExtension.class)
class RequisitionServiceImplTest {

    static final long HEAD = 10L;      // heads department 5
    static final long OTHER = 11L;     // an ordinary employee
    static final long HR_USER = 12L;
    static final long DEPT = 5L;

    @Mock JobRequisitionRepository requisitions;
    @Mock RequisitionApprovalRepository approvals;
    @Mock RecruitmentOrgLookup lookup;
    @Mock EmployeeAccessGuard guard;
    @Mock AuditTrail audit;
    @Mock EntityManager entityManager;

    RequisitionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RequisitionServiceImpl(requisitions, approvals, lookup, new RequisitionWorkflow(), guard, audit, entityManager);
    }

    // ----------------------------------------------------------------------------------- fixtures

    private void caller(long employeeId, boolean hr) {
        when(guard.currentEmployeeId()).thenReturn(employeeId);
        lenient().when(guard.isHrStaff()).thenReturn(hr);
        lenient().when(lookup.isDepartmentHead(DEPT, HEAD)).thenReturn(true);
    }

    private RequisitionRequest request() {
        return new RequisitionRequest(" Analyst ", "  ", DEPT, 7L, null, null, 2, EmploymentType.FULL_TIME,
            RequisitionType.NEW, null, new BigDecimal("2.0"), new BigDecimal("5.0"), new BigDecimal("500.000"),
            new BigDecimal("900.000"), RequisitionPriority.MEDIUM, LocalDate.now().plusDays(30), " Growth ", 0L);
    }

    private RequisitionRequest with(java.util.function.UnaryOperator<RequisitionRequest> change) {
        return change.apply(request());
    }

    private JobRequisition existing(RequisitionStatus status) {
        return JobRequisition.builder().id(1L).code("REQ-2026-000001").jobTitle("Analyst").departmentId(DEPT)
            .designationId(7L).requestedBy(HEAD).noOfPositions(1).filledPositions(0)
            .employmentType(EmploymentType.FULL_TIME).requisitionType(RequisitionType.NEW)
            .minExperienceYears(BigDecimal.ZERO).currency("SSP").priority(RequisitionPriority.MEDIUM)
            .status(status).version(3L).build();
    }

    private void found(JobRequisition r) {
        when(requisitions.findById(1L)).thenReturn(Optional.of(r));
        lenient().when(requisitions.save(any(JobRequisition.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static RequisitionActionRequest at(long version) {
        return new RequisitionActionRequest(version);
    }

    // ------------------------------------------------------------------------------------- create

    @Test
    void create_byDepartmentHead_setsEverythingTheServerOwnsAndStartsAsDraft() {
        caller(HEAD, false);
        when(requisitions.nextCodeNumber()).thenReturn(7L);
        when(requisitions.save(any(JobRequisition.class))).thenAnswer(i -> i.getArgument(0));

        RequisitionResponse response = service.create(request());

        ArgumentCaptor<JobRequisition> saved = ArgumentCaptor.forClass(JobRequisition.class);
        verify(requisitions).save(saved.capture());
        JobRequisition r = saved.getValue();
        assertTrue(r.getCode().matches("REQ-\\d{4}-000007"));
        assertEquals(DRAFT, r.getStatus());
        assertEquals(HEAD, r.getRequestedBy());
        assertEquals("SSP", r.getCurrency());
        assertEquals(0, r.getFilledPositions());
        assertEquals("Analyst", r.getJobTitle());
        assertNull(r.getJobTitleAr());
        assertEquals("Growth", r.getJustification());
        assertEquals(DRAFT, response.status());
        verify(audit).record(eq("REQUISITION_CREATED"), eq("REQUISITION"), any(), anyString());
    }

    @Test
    void create_byEmployeeWhoDoesNotHeadTheDepartment_isDenied() {
        caller(OTHER, false);
        assertThrows(AccessDeniedException.class, () -> service.create(request()));
        verify(requisitions, never()).save(any());
    }

    @Test
    void create_byHrStaff_needsNoDepartmentHeadship() {
        caller(HR_USER, true);
        when(requisitions.nextCodeNumber()).thenReturn(1L);
        when(requisitions.save(any(JobRequisition.class))).thenAnswer(i -> i.getArgument(0));
        service.create(request());
        verify(requisitions).save(any(JobRequisition.class));
    }

    @Test
    void create_withAnAccountNotLinkedToAnEmployee_isRefused() {
        when(guard.currentEmployeeId()).thenReturn(null);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.create(request()));
        assertEquals("NO_EMPLOYEE_PROFILE", ex.getRuleCode());
    }

    @Test
    void create_replacementMustNameTheReplacedEmployee() {
        caller(HEAD, false);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.create(with(r -> new RequisitionRequest(r.jobTitle(), r.jobTitleAr(), r.departmentId(), r.designationId(),
                null, null, 1, r.employmentType(), RequisitionType.REPLACEMENT, null, r.minExperienceYears(), null, null, null,
                r.priority(), null, null, null))));
        assertEquals("REPLACED_EMPLOYEE_REQUIRED", ex.getRuleCode());
    }

    @Test
    void create_rejectsInvertedRangesAndPastTargetDate() {
        caller(HEAD, false);
        RequisitionRequest base = request();
        assertThrows(BadRequestException.class, () -> service.create(new RequisitionRequest(base.jobTitle(), null, DEPT, 7L, null, null,
            1, base.employmentType(), base.requisitionType(), null, new BigDecimal("5"), new BigDecimal("2"), null, null,
            base.priority(), null, null, null)));
        assertThrows(BadRequestException.class, () -> service.create(new RequisitionRequest(base.jobTitle(), null, DEPT, 7L, null, null,
            1, base.employmentType(), base.requisitionType(), null, BigDecimal.ZERO, null, new BigDecimal("900"), new BigDecimal("100"),
            base.priority(), null, null, null)));
        assertThrows(BadRequestException.class, () -> service.create(new RequisitionRequest(base.jobTitle(), null, DEPT, 7L, null, null,
            1, base.employmentType(), base.requisitionType(), null, BigDecimal.ZERO, null, null, null,
            base.priority(), LocalDate.now().minusDays(1), null, null)));
        verify(requisitions, never()).save(any());
    }

    // ------------------------------------------------------------------------------------- update

    @Test
    void update_needsAVersion_andRefusesAStaleOne() {
        caller(HEAD, false);
        found(existing(DRAFT));
        RequisitionRequest noVersion = new RequisitionRequest("T", null, DEPT, 7L, null, null, 1, EmploymentType.FULL_TIME,
            RequisitionType.NEW, null, BigDecimal.ZERO, null, null, null, RequisitionPriority.LOW, null, null, null);
        assertThrows(BadRequestException.class, () -> service.update(1L, noVersion));
        assertThrows(OptimisticLockingFailureException.class, () -> service.update(1L, request())); // sends 0, current is 3
    }

    @Test
    void update_isOnlyForDrafts() {
        caller(HEAD, false);
        found(existing(PENDING_APPROVAL));
        RequisitionRequest current = new RequisitionRequest("T", null, DEPT, 7L, null, null, 1, EmploymentType.FULL_TIME,
            RequisitionType.NEW, null, BigDecimal.ZERO, null, null, null, RequisitionPriority.LOW, null, null, 3L);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.update(1L, current));
        assertEquals("REQUISITION_NOT_EDITABLE", ex.getRuleCode());
    }

    @Test
    void update_byAnotherEmployee_isDenied() {
        caller(OTHER, false);
        found(existing(DRAFT));
        assertThrows(AccessDeniedException.class, () -> service.update(1L, request()));
    }

    // -------------------------------------------------------------------------------- submit/cancel

    @Test
    void submit_movesDraftToPending_andOnlyOnce() {
        caller(HEAD, false);
        JobRequisition r = existing(DRAFT);
        found(r);
        assertEquals(PENDING_APPROVAL, service.submit(1L, at(3)).status());
        assertThrows(BusinessRuleException.class, () -> service.submit(1L, at(3))); // now PENDING_APPROVAL: 409
    }

    @Test
    void submit_byAnotherEmployee_isDenied() {
        caller(OTHER, false);
        found(existing(DRAFT));
        assertThrows(AccessDeniedException.class, () -> service.submit(1L, at(3)));
    }

    @Test
    void cancel_requesterMayCancelWhileEarly_butNotAfterApproval() {
        caller(HEAD, false);
        found(existing(PENDING_APPROVAL));
        assertEquals(CANCELLED, service.cancel(1L, at(3)).status());

        found(existing(APPROVED));
        assertThrows(AccessDeniedException.class, () -> service.cancel(1L, at(3)));
    }

    @Test
    void cancel_hrMayCancelAnApprovedRequisition_butNotARejectedOne() {
        caller(HR_USER, true);
        found(existing(APPROVED));
        JobRequisition cancelled = existing(APPROVED);
        found(cancelled);
        assertEquals(CANCELLED, service.cancel(1L, at(3)).status());
        assertNotNull(cancelled.getClosedAt());

        found(existing(REJECTED));
        assertThrows(BusinessRuleException.class, () -> service.cancel(1L, at(3)));
    }

    // ----------------------------------------------------------------------------- approve/reject

    @Test
    void approve_recordsTheConfirmationsAndMovesToApproved() {
        caller(HR_USER, true);
        found(existing(PENDING_APPROVAL));

        RequisitionResponse response = service.approve(1L, new ApproveRequisitionRequest(true, true, " ok ", 3L));

        assertEquals(APPROVED, response.status());
        ArgumentCaptor<RequisitionApproval> row = ArgumentCaptor.forClass(RequisitionApproval.class);
        verify(approvals).save(row.capture());
        assertEquals(ApprovalDecision.APPROVED, row.getValue().getDecision());
        assertEquals(HR_USER, row.getValue().getApproverId());
        assertTrue(row.getValue().getBudgetConfirmed());
        assertTrue(row.getValue().getManagementConfirmed());
        assertEquals("ok", row.getValue().getComments());
        assertEquals(1, row.getValue().getLevelNo());
    }

    @Test
    void approve_nobodyApprovesTheirOwnRequisition() {
        caller(HEAD, true); // an HR user who raised it
        found(existing(PENDING_APPROVAL));
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.approve(1L, new ApproveRequisitionRequest(true, true, null, 3L)));
        assertEquals("SELF_APPROVAL_NOT_ALLOWED", ex.getRuleCode());
        verify(approvals, never()).save(any());
    }

    @Test
    void approve_isHrOnly_andTheRoleCheckComesBeforeAnyStateRule() {
        when(guard.isHrStaff()).thenReturn(false);
        found(existing(APPROVED)); // wrong state too: the caller must still just be told "no"
        assertThrows(AccessDeniedException.class,
            () -> service.approve(1L, new ApproveRequisitionRequest(true, true, null, 3L)));
        verify(approvals, never()).save(any());
    }

    @Test
    void approve_twiceIsRefused_andWritesNoSecondDecision() {
        caller(HR_USER, true);
        found(existing(APPROVED));
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.approve(1L, new ApproveRequisitionRequest(true, true, null, 3L)));
        assertEquals("REQUISITION_INVALID_STATE", ex.getRuleCode());
        verify(approvals, never()).save(any());
    }

    @Test
    void approve_fromAStaleScreen_isRefused() {
        when(guard.isHrStaff()).thenReturn(true);
        found(existing(PENDING_APPROVAL));
        assertThrows(OptimisticLockingFailureException.class,
            () -> service.approve(1L, new ApproveRequisitionRequest(true, true, null, 2L)));
        verify(approvals, never()).save(any());
    }

    @Test
    void reject_keepsTheReasonOnTheDecisionRow() {
        caller(HR_USER, true);
        found(existing(PENDING_APPROVAL));

        assertEquals(REJECTED, service.reject(1L, new RejectRequisitionRequest("No budget this year", 3L)).status());

        ArgumentCaptor<RequisitionApproval> row = ArgumentCaptor.forClass(RequisitionApproval.class);
        verify(approvals).save(row.capture());
        assertEquals(ApprovalDecision.REJECTED, row.getValue().getDecision());
        assertEquals("No budget this year", row.getValue().getComments());
        assertFalse(row.getValue().getBudgetConfirmed());
    }

    @Test
    void holdResumeAndClose_followTheWorkflow() {
        when(guard.isHrStaff()).thenReturn(true);
        JobRequisition r = existing(APPROVED);
        found(r);
        assertEquals(ON_HOLD, service.hold(1L, at(3)).status());
        assertEquals(APPROVED, service.resume(1L, at(3)).status());
        assertEquals(CLOSED, service.close(1L, at(3)).status());
        assertNotNull(r.getClosedAt());
        assertThrows(BusinessRuleException.class, () -> service.resume(1L, at(3))); // closed is final
    }

    @Test
    void hold_isHrOnly() {
        when(guard.isHrStaff()).thenReturn(false);
        found(existing(APPROVED));
        assertThrows(AccessDeniedException.class, () -> service.hold(1L, at(3)));
    }

    // ------------------------------------------------------------------------------------- reads

    @Test
    void get_isLimitedToHrTheRequesterAndTheDepartmentHead() {
        found(existing(DRAFT));

        caller(OTHER, false);
        assertThrows(AccessDeniedException.class, () -> service.get(1L));

        when(guard.currentEmployeeId()).thenReturn(HEAD);
        assertEquals(1L, service.get(1L).id());

        when(guard.isHrStaff()).thenReturn(true);
        assertEquals(1L, service.get(1L).id());
    }

    @Test
    void get_unknownIdIs404() {
        when(requisitions.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.get(99L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_capsThePageSizeAndIgnoresUnknownSortColumns() {
        when(guard.isHrStaff()).thenReturn(true);
        when(requisitions.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.search(null, null, null, -4, 5000, "PASSWORD; DROP TABLE", "sideways");

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(requisitions).findAll(any(Specification.class), page.capture());
        assertEquals(0, page.getValue().getPageNumber());
        assertEquals(100, page.getValue().getPageSize());
        assertNotNull(page.getValue().getSort().getOrderFor("createdAt"));
        assertNotNull(page.getValue().getSort().getOrderFor("id"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_forAnOrdinaryEmployeeIsRestrictedToWhatTheySeeAndHead() {
        caller(OTHER, false);
        when(lookup.departmentIdsHeadedBy(OTHER)).thenReturn(List.of());
        when(requisitions.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.search(null, null, null, 0, 20, "createdAt", "desc");

        verify(lookup).departmentIdsHeadedBy(OTHER);
    }

    // ----------------------------------------------------------------------------------- form options

    private static Department department(long id, String name, Long head) {
        return Department.builder().deptId(id).name(name).nameAr(name).headEmployeeId(head).isActive(true).build();
    }

    private static Designation designation(long id, Long departmentId) {
        return Designation.builder().desigId(id).title("T" + id).departmentId(departmentId).isActive(true).build();
    }

    @Test
    void raiseOptions_forAHead_listOnlyTheirDepartmentsAndTheDesignationsThatFit() {
        when(guard.currentEmployeeId()).thenReturn(HEAD);
        when(guard.isHrStaff()).thenReturn(false);
        when(lookup.departmentsRaisableBy(HEAD, false)).thenReturn(List.of(department(DEPT, "Finance", HEAD)));
        when(lookup.activeDesignations()).thenReturn(List.of(designation(1, DEPT), designation(2, 99L), designation(3, null)));
        when(lookup.activeLocations()).thenReturn(List.of());

        RaiseOptionsResponse options = service.raiseOptions();

        assertEquals(List.of(DEPT), options.departments().stream().map(RaiseOptionsResponse.Option::id).toList());
        // The other department's designation (2) is left out; the one without a department (3) fits anywhere.
        assertEquals(List.of(1L, 3L), options.designations().stream().map(RaiseOptionsResponse.DesignationOption::id).toList());
    }

    @Test
    void peopleOf_isLimitedToDepartmentsTheCallerMayRaiseFor() {
        caller(OTHER, false);
        assertThrows(AccessDeniedException.class, () -> service.peopleOf(DEPT));
        verify(lookup, never()).activeEmployeesOf(any());

        when(guard.currentEmployeeId()).thenReturn(HEAD);
        Employee e = Employee.builder().id(30L).employeeCode("EMP-1").firstName("Ayen").lastName("Deng")
            .firstNameAr("آين").lastNameAr("دينق").build();
        when(lookup.activeEmployeesOf(DEPT)).thenReturn(List.of(e));
        List<PersonOptionResponse> people = service.peopleOf(DEPT);
        assertEquals(1, people.size());
        assertEquals("Ayen Deng", people.get(0).name());
    }
}
