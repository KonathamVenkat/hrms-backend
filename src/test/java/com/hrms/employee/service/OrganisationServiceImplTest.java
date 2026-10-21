package com.hrms.employee.service;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.BranchPlacementRequest;
import com.hrms.employee.dto.response.BranchLookupResponse;
import com.hrms.employee.dto.response.BranchPlacementResponse;
import com.hrms.employee.entity.CompanyBranch;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.entity.OfficeLocation;
import com.hrms.employee.repository.CompanyBranchRepository;
import com.hrms.employee.dto.request.BranchAssignmentRequest;
import com.hrms.employee.dto.request.BranchAssignmentRequest.Item;
import com.hrms.employee.dto.response.BranchAssignmentResult;
import com.hrms.employee.dto.response.BranchAssignmentResult.Skipped;
import com.hrms.employee.dto.response.EmployeeBranchGapResponse;
import com.hrms.employee.dto.response.OrganisationSummaryResponse;
import com.hrms.employee.entity.EmployeeJobDetails;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.repository.OfficeLocationRepository;
import com.hrms.employee.repository.projection.EmployeeBranchGapProjection;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import com.hrms.employee.service.impl.OrganisationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Branch placement rules: a branch sits in one office location and has one manager; one person may manage several
 * branches; only an active employee can manage; a stale edit is refused; deactivating a person clears every branch.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrganisationServiceImplTest {

    @Mock CompanyBranchRepository branchRepository;
    @Mock OfficeLocationRepository locationRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock EmployeeJobDetailsRepository jobDetailsRepository;
    @Mock AuditTrail audit;

    @InjectMocks OrganisationServiceImpl service;

    CompanyBranch juba;
    CompanyBranch wau;
    OfficeLocation location;
    Employee manager;

    @BeforeEach
    void setUp() {
        juba = CompanyBranch.builder().id(1L).companyId(1L).code("JUBA").name("Juba").nameAr("جوبا")
            .isActive(true).version(3L).build();
        wau = CompanyBranch.builder().id(2L).companyId(1L).code("WAU").name("Wau").nameAr("واو")
            .isActive(true).version(0L).build();
        location = OfficeLocation.builder().locationId(7L).locationName("Juba HQ").locationNameAr("جوبا").isActive(1).build();
        manager = mock(Employee.class);
        when(manager.getId()).thenReturn(5L);
        when(manager.getIsActive()).thenReturn(true);
        when(manager.getFullName()).thenReturn("Lual Deng");
        when(manager.getEmployeeCode()).thenReturn("EMP-2026-000005");
        when(branchRepository.findById(1L)).thenReturn(Optional.of(juba));
        when(branchRepository.findById(2L)).thenReturn(Optional.of(wau));
        when(branchRepository.save(any(CompanyBranch.class))).thenAnswer(i -> i.getArgument(0));
        when(locationRepository.findById(7L)).thenReturn(Optional.of(location));
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(manager));
        when(employeeRepository.findAllById(any())).thenReturn(List.of(manager));
    }

    @Test
    void setPlacement_setsLocationAndManager_andAudits() {
        BranchPlacementResponse r = service.setPlacement(1L, new BranchPlacementRequest(7L, 5L, 3L));

        assertEquals(7L, juba.getLocationId());
        assertEquals(5L, juba.getBranchManagerEmployeeId());
        assertEquals("Lual Deng", r.managerName());
        assertEquals("Juba HQ", r.locationName());
        verify(audit).record("BRANCH_PLACEMENT_SET", "COMPANY_BRANCH", 1L, "location=7, manager=5");
    }

    @Test
    void movingABranchToAnotherLocation_isRefusedWhileEmployeesBelongToIt() {
        juba.setLocationId(7L);
        OfficeLocation other = OfficeLocation.builder().locationId(8L).locationName("Wau").locationNameAr("w").isActive(1).build();
        when(locationRepository.findById(8L)).thenReturn(Optional.of(other));
        when(jobDetailsRepository.countCurrentJobsInBranch(1L)).thenReturn(30L);

        BusinessRuleException e = assertThrows(BusinessRuleException.class,
            () -> service.setPlacement(1L, new BranchPlacementRequest(8L, 5L, 3L)));

        assertEquals("BRANCH_HAS_EMPLOYEES", e.getRuleCode());
        assertEquals(7L, juba.getLocationId());
        verify(branchRepository, never()).save(any());
    }

    @Test
    void movingABranchWithNoEmployees_isAllowed_andKeepingItsLocationNeverChecksEmployees() {
        juba.setLocationId(7L);
        OfficeLocation other = OfficeLocation.builder().locationId(8L).locationName("Wau").locationNameAr("w").isActive(1).build();
        when(locationRepository.findById(8L)).thenReturn(Optional.of(other));
        when(jobDetailsRepository.countCurrentJobsInBranch(1L)).thenReturn(0L);

        service.setPlacement(1L, new BranchPlacementRequest(8L, 5L, 3L));
        assertEquals(8L, juba.getLocationId());

        service.setPlacement(1L, new BranchPlacementRequest(8L, null, 3L));   // same location again: manager change only
        verify(jobDetailsRepository, times(1)).countCurrentJobsInBranch(1L);
    }

    @Test
    void sameManagerOnTwoBranches_isAllowed() {
        service.setPlacement(1L, new BranchPlacementRequest(7L, 5L, 3L));
        service.setPlacement(2L, new BranchPlacementRequest(7L, 5L, 0L));

        assertEquals(5L, juba.getBranchManagerEmployeeId());
        assertEquals(5L, wau.getBranchManagerEmployeeId());
    }

    @Test
    void setPlacement_unknownLocation_throwsResourceNotFound() {
        assertThrows(ResourceNotFoundException.class,
            () -> service.setPlacement(1L, new BranchPlacementRequest(999L, null, 3L)));
        verify(branchRepository, never()).save(any());
    }

    @Test
    void setPlacement_inactiveEmployee_throwsBusinessRule() {
        when(manager.getIsActive()).thenReturn(false);

        BusinessRuleException e = assertThrows(BusinessRuleException.class,
            () -> service.setPlacement(1L, new BranchPlacementRequest(7L, 5L, 3L)));

        assertEquals("MANAGER_NOT_ACTIVE", e.getRuleCode());
        verify(branchRepository, never()).save(any());
    }

    @Test
    void setPlacement_unknownEmployee_throwsResourceNotFound() {
        assertThrows(ResourceNotFoundException.class,
            () -> service.setPlacement(1L, new BranchPlacementRequest(7L, 404L, 3L)));
    }

    @Test
    void setPlacement_staleVersion_throwsOptimisticLockFailure() {
        assertThrows(OptimisticLockingFailureException.class,
            () -> service.setPlacement(1L, new BranchPlacementRequest(7L, 5L, 2L)));
        verify(branchRepository, never()).save(any());
    }

    @Test
    void setPlacement_nullManager_clearsTheManager() {
        juba.setBranchManagerEmployeeId(5L);

        BranchPlacementResponse r = service.setPlacement(1L, new BranchPlacementRequest(7L, null, 3L));

        assertNull(juba.getBranchManagerEmployeeId());
        assertNull(r.managerEmployeeId());
        verify(audit).record("BRANCH_PLACEMENT_SET", "COMPANY_BRANCH", 1L, "location=7, manager=null");
    }

    @Test
    void setPlacement_inactiveBranch_throwsBusinessRule() {
        juba.setIsActive(false);

        BusinessRuleException e = assertThrows(BusinessRuleException.class,
            () -> service.setPlacement(1L, new BranchPlacementRequest(7L, 5L, 3L)));

        assertEquals("BRANCH_INACTIVE", e.getRuleCode());
    }

    @Test
    void clearManagerOf_clearsEveryBranchOfThatEmployee() {
        juba.setBranchManagerEmployeeId(5L);
        wau.setBranchManagerEmployeeId(5L);
        when(branchRepository.findByBranchManagerEmployeeId(5L)).thenReturn(List.of(juba, wau));

        service.clearManagerOf(5L);

        assertNull(juba.getBranchManagerEmployeeId());
        assertNull(wau.getBranchManagerEmployeeId());
        verify(audit).record("BRANCH_MANAGER_CLEARED", "COMPANY_BRANCH", 1L, "manager=5");
        verify(audit).record("BRANCH_MANAGER_CLEARED", "COMPANY_BRANCH", 2L, "manager=5");
    }

    @Test
    void lookup_returnsOnlyActiveBranchesOfTheLocation() {
        juba.setLocationId(7L);
        when(branchRepository.findByLocationIdAndIsActiveTrueOrderByNameAsc(7L)).thenReturn(List.of(juba));

        List<BranchLookupResponse> list = service.lookupBranches(7L);

        assertEquals(1, list.size());
        assertEquals(new BranchLookupResponse(1L, "Juba", "جوبا", 7L), list.get(0));
    }

    @Test
    void lookup_withoutLocation_returnsEveryActiveBranch() {
        when(branchRepository.findByIsActiveTrueOrderByNameAsc()).thenReturn(List.of(juba, wau));

        assertEquals(2, service.lookupBranches(null).size());
    }

    // ── Gap report and bulk assignment (back-fill) ────────────

    private EmployeeJobDetails currentJob(Long employeeId, Long locationId, Long branchId) {
        return EmployeeJobDetails.builder().employeeId(employeeId).departmentId(10L).designationId(20L)
            .locationId(locationId).companyBranchId(branchId).build();
    }

    private void stubEmployeeWithJob(Long employeeId, Long locationId, Long branchId) {
        Employee e = mock(Employee.class);
        when(e.getIsActive()).thenReturn(true);
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(e));
        when(jobDetailsRepository.findByEmployeeIdAndIsCurrent(employeeId, 1))
            .thenReturn(Optional.of(currentJob(employeeId, locationId, branchId)));
        when(jobDetailsRepository.setBranchOnCurrentJob(eq(employeeId), anyLong(), any())).thenReturn(1);
    }

    @Test
    void assignBranches_appliesValidRows_andListsSkippedWithReasons() {
        juba.setLocationId(7L);
        CompanyBranch closed = CompanyBranch.builder().id(3L).code("OLD").isActive(false).locationId(7L).version(0L).build();
        CompanyBranch elsewhere = CompanyBranch.builder().id(4L).code("ELSE").isActive(true).locationId(8L).version(0L).build();
        when(branchRepository.findById(3L)).thenReturn(Optional.of(closed));
        when(branchRepository.findById(4L)).thenReturn(Optional.of(elsewhere));
        stubEmployeeWithJob(11L, 7L, null);                       // valid
        stubEmployeeWithJob(12L, 7L, null);                       // inactive branch
        when(employeeRepository.findById(13L)).thenReturn(Optional.empty());   // unknown employee
        Employee noJob = mock(Employee.class);
        when(noJob.getIsActive()).thenReturn(true);
        when(employeeRepository.findById(14L)).thenReturn(Optional.of(noJob));
        when(jobDetailsRepository.findByEmployeeIdAndIsCurrent(14L, 1)).thenReturn(Optional.empty());
        stubEmployeeWithJob(15L, 7L, null);                       // branch is in another location
        stubEmployeeWithJob(16L, 7L, 2L);                         // already has a branch
        Employee gone = mock(Employee.class);
        when(gone.getIsActive()).thenReturn(false);
        when(employeeRepository.findById(17L)).thenReturn(Optional.of(gone));

        BranchAssignmentResult r = service.assignBranches(new BranchAssignmentRequest(List.of(
            new Item(11L, 1L), new Item(12L, 3L), new Item(13L, 1L), new Item(14L, 1L),
            new Item(15L, 4L), new Item(16L, 1L), new Item(17L, 1L), new Item(11L, 99L))));

        assertEquals(1, r.assigned());
        assertEquals(List.of(
            new Skipped(12L, "BRANCH_INACTIVE"), new Skipped(13L, "EMPLOYEE_NOT_FOUND"),
            new Skipped(14L, "NO_CURRENT_JOB"), new Skipped(15L, "BRANCH_LOCATION_MISMATCH"),
            new Skipped(16L, "ALREADY_ASSIGNED"), new Skipped(17L, "EMPLOYEE_INACTIVE"),
            new Skipped(11L, "BRANCH_NOT_FOUND")), r.skipped());
        verify(jobDetailsRepository, times(1)).setBranchOnCurrentJob(eq(11L), eq(1L), any());
    }

    @Test
    void assignBranches_audits_oneEventPerAssignedRow() {
        juba.setLocationId(7L);
        wau.setLocationId(7L);
        stubEmployeeWithJob(11L, 7L, null);
        stubEmployeeWithJob(12L, 7L, null);

        BranchAssignmentResult r = service.assignBranches(
            new BranchAssignmentRequest(List.of(new Item(11L, 1L), new Item(12L, 2L))));

        assertEquals(2, r.assigned());
        verify(audit).record("EMPLOYEE_BRANCH_ASSIGNED", "EMPLOYEE", 11L, "branch=1");
        verify(audit).record("EMPLOYEE_BRANCH_ASSIGNED", "EMPLOYEE", 12L, "branch=2");
    }

    @Test
    void assignBranches_losingARaceIsReportedAsAlreadyAssigned() {
        juba.setLocationId(7L);
        stubEmployeeWithJob(11L, 7L, null);
        when(jobDetailsRepository.setBranchOnCurrentJob(eq(11L), anyLong(), any())).thenReturn(0);

        BranchAssignmentResult r = service.assignBranches(new BranchAssignmentRequest(List.of(new Item(11L, 1L))));

        assertEquals(0, r.assigned());
        assertEquals(List.of(new Skipped(11L, "ALREADY_ASSIGNED")), r.skipped());
        verify(audit, never()).record(eq("EMPLOYEE_BRANCH_ASSIGNED"), any(), any(), any());
    }

    private EmployeeBranchGapProjection gap(long id) {
        EmployeeBranchGapProjection p = mock(EmployeeBranchGapProjection.class);
        when(p.getEmployeeId()).thenReturn(id);
        when(p.getEmployeeCode()).thenReturn("EMP-2026-00000" + id);
        when(p.getFirstName()).thenReturn("Lual");
        when(p.getLastName()).thenReturn("Deng");
        when(p.getFirstNameAr()).thenReturn("ع");
        when(p.getLastNameAr()).thenReturn("ل");
        when(p.getDepartmentName()).thenReturn("IT");
        when(p.getLocationId()).thenReturn(7L);
        when(p.getLocationName()).thenReturn("Juba HQ");
        return p;
    }

    @Test
    void employeesWithoutBranch_capsPageSizeAt100_andMapsRows() {
        EmployeeBranchGapProjection row = gap(1);   // built first: stubbing cannot be nested
        when(jobDetailsRepository.findEmployeesWithoutBranch(any(), any()))
            .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 100), 1));

        Page<EmployeeBranchGapResponse> page = service.employeesWithoutBranch(null, PageRequest.of(2, 1000));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(jobDetailsRepository).findEmployeesWithoutBranch(any(), captor.capture());
        assertEquals(100, captor.getValue().getPageSize());
        assertEquals(2, captor.getValue().getPageNumber());
        assertEquals(new EmployeeBranchGapResponse(1L, "EMP-2026-000001", "Lual Deng", "ع ل", "IT", 7L, "Juba HQ"),
            page.getContent().get(0));
    }

    @Test
    void employeesWithoutBranch_escapesLikeWildcards_andBlankMeansNoFilter() {
        when(jobDetailsRepository.findEmployeesWithoutBranch(any(), any())).thenReturn(Page.empty());

        service.employeesWithoutBranch(" 50%_a ", PageRequest.of(0, 20));
        service.employeesWithoutBranch("   ", PageRequest.of(0, 20));

        verify(jobDetailsRepository).findEmployeesWithoutBranch(eq("50\\%\\_a"), any());
        verify(jobDetailsRepository).findEmployeesWithoutBranch(isNull(), any());
    }

    @Test
    void managerCandidates_capsPageSize_escapesKeyword_andPassesLocation() {
        EmployeeBranchGapProjection row = gap(1);
        when(jobDetailsRepository.findManagerCandidates(any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 100), 1));

        Page<EmployeeBranchGapResponse> page = service.managerCandidates(7L, " 50%_a ", PageRequest.of(2, 1000));
        service.managerCandidates(null, "   ", PageRequest.of(0, 20));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(jobDetailsRepository).findManagerCandidates(eq(7L), eq("50\\%\\_a"), captor.capture());
        verify(jobDetailsRepository).findManagerCandidates(isNull(), isNull(), any());
        assertEquals(100, captor.getValue().getPageSize());
        assertEquals(2, captor.getValue().getPageNumber());
        assertEquals("Lual Deng", page.getContent().get(0).fullName());
    }

    @Test
    void summary_countsAllThreeGaps() {
        when(jobDetailsRepository.countEmployeesWithoutBranch()).thenReturn(12L);
        when(branchRepository.countByIsActiveTrueAndBranchManagerEmployeeIdIsNull()).thenReturn(3L);
        when(branchRepository.countByIsActiveTrueAndLocationIdIsNull()).thenReturn(2L);

        assertEquals(new OrganisationSummaryResponse(12L, 3L, 2L), service.summary());
    }
}
