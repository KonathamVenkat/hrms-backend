package com.hrms.employee.service;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.JobDetailsRequest;
import com.hrms.employee.entity.Department;
import com.hrms.employee.entity.Designation;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.entity.EmployeeJobDetails;
import com.hrms.employee.entity.OfficeLocation;
import com.hrms.employee.repository.*;
import com.hrms.employee.service.impl.JobDetailsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobDetailsServiceImplTest {

    @Mock EmployeeJobDetailsRepository jobDetailsRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock DesignationRepository designationRepository;
    @Mock OfficeLocationRepository locationRepository;
    @Mock WorkShiftRepository shiftRepository;
    @Mock com.hrms.employee.service.DepartmentHeadService departmentHeads;
    @Mock com.hrms.employee.repository.CompanyBranchRepository branchRepository;

    JobDetailsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new JobDetailsServiceImpl(jobDetailsRepository, employeeRepository,
            departmentRepository, designationRepository, locationRepository, shiftRepository, departmentHeads, branchRepository);

        // Happy-path references: active employee 5, department 10, designation 20 in 10, location 30.
        when(employeeRepository.findByIdAndIsActive(5L, true)).thenReturn(Optional.of(mock(Employee.class)));
        Department dept = mock(Department.class);
        when(dept.getIsActive()).thenReturn(true);
        when(departmentRepository.findById(10L)).thenReturn(Optional.of(dept));
        Designation desig = mock(Designation.class);
        when(desig.getIsActive()).thenReturn(true);
        when(desig.getDepartmentId()).thenReturn(10L);
        when(designationRepository.findById(20L)).thenReturn(Optional.of(desig));
        OfficeLocation loc = mock(OfficeLocation.class);
        when(loc.getIsActive()).thenReturn(1);
        when(locationRepository.findById(30L)).thenReturn(Optional.of(loc));
    }

    private JobDetailsRequest request(LocalDate effectiveFrom) {
        return JobDetailsRequest.builder()
            .departmentId(10L).designationId(20L).locationId(30L)
            .effectiveFrom(effectiveFrom).build();
    }

    @Test
    void deactivatedEmployeeCannotBeAssignedAJob() {
        when(employeeRepository.findByIdAndIsActive(6L, true)).thenReturn(Optional.empty());
        when(employeeRepository.existsById(6L)).thenReturn(true);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.assignJob(6L, request(LocalDate.now())));
        assertEquals("EMP_INACTIVE", ex.getRuleCode());
    }

    @Test
    void unknownEmployeeIsNotFound() {
        when(employeeRepository.findByIdAndIsActive(6L, true)).thenReturn(Optional.empty());
        when(employeeRepository.existsById(6L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
            () -> service.assignJob(6L, request(LocalDate.now())));
    }

    @Test
    void designationMustBelongToTheSelectedDepartment() {
        Designation other = mock(Designation.class);
        when(other.getIsActive()).thenReturn(true);
        when(other.getDepartmentId()).thenReturn(99L);
        when(designationRepository.findById(20L)).thenReturn(Optional.of(other));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.assignJob(5L, request(LocalDate.now())));
        assertEquals("DESIGNATION_DEPARTMENT_MISMATCH", ex.getRuleCode());
    }

    @Test
    void inactiveDepartmentIsRejected() {
        Department inactive = mock(Department.class);
        when(inactive.getIsActive()).thenReturn(false);
        when(departmentRepository.findById(10L)).thenReturn(Optional.of(inactive));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.assignJob(5L, request(LocalDate.now())));
        assertEquals("DEPARTMENT_INACTIVE", ex.getRuleCode());
    }

    @Test
    void employeeCannotBeTheirOwnReportingManager() {
        JobDetailsRequest req = request(LocalDate.now());
        req.setReportingManagerId(5L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.assignJob(5L, req));
        assertEquals("MANAGER_IS_SELF", ex.getRuleCode());
    }

    @Test
    void inactiveManagerIsRejected() {
        when(employeeRepository.findByIdAndIsActive(7L, true)).thenReturn(Optional.empty());
        JobDetailsRequest req = request(LocalDate.now());
        req.setFunctionalManagerId(7L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.assignJob(5L, req));
        assertEquals("MANAGER_INVALID", ex.getRuleCode());
    }

    @Test
    void effectiveDateMoreThanAYearInThePastIsRejected() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.assignJob(5L, request(LocalDate.now().minusYears(1).minusDays(1))));
        assertEquals("INVALID_DATE", ex.getRuleCode());
        verify(jobDetailsRepository, never()).closeCurrentRecord(anyLong(), any(), anyString());
    }

    @Test
    void futureDatedAssignmentIsRejected() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.assignJob(5L, request(LocalDate.now().plusDays(30))));
        assertEquals("FUTURE_EFFECTIVE_FROM", ex.getRuleCode());
        verify(jobDetailsRepository, never()).closeCurrentRecord(anyLong(), any(), anyString());
        verify(jobDetailsRepository, never()).save(any());
    }

    @Test
    void assignmentStartingTomorrowIsRejected() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.assignJob(5L, request(LocalDate.now().plusDays(1))));
        assertEquals("FUTURE_EFFECTIVE_FROM", ex.getRuleCode());
    }

    @Test
    void assignmentStartingTodayIsAllowed() {
        when(jobDetailsRepository.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.empty());
        when(jobDetailsRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThrows(java.util.NoSuchElementException.class,   // reaches the final lookup, i.e. past the date check
            () -> service.assignJob(5L, request(LocalDate.now())));
        verify(jobDetailsRepository).save(any());
        verify(departmentHeads).clearHeadOf(5L, 10L);
    }

    @Test
    void jobChangesLockTheEmployeeRowFirst() {
        JobDetailsRequest req = request(LocalDate.now().plusDays(30));
        assertThrows(BusinessRuleException.class, () -> service.assignJob(5L, req));

        var order = inOrder(employeeRepository);
        order.verify(employeeRepository).findByIdForUpdate(5L);
        order.verify(employeeRepository).findByIdAndIsActive(5L, true);
    }

    @Test
    void newAssignmentMustStartAfterTheCurrentOne() {
        LocalDate currentFrom = LocalDate.now().minusDays(30);
        EmployeeJobDetails current = mock(EmployeeJobDetails.class);
        when(current.getEffectiveFrom()).thenReturn(currentFrom);
        when(jobDetailsRepository.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.of(current));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.assignJob(5L, request(currentFrom)));
        assertEquals("INVALID_EFFECTIVE_FROM", ex.getRuleCode());
        verify(jobDetailsRepository, never()).closeCurrentRecord(anyLong(), any(), anyString());
        verify(jobDetailsRepository, never()).save(any());
    }

// ── Branch (workflow phase 1) ─────────────────────────────

    private com.hrms.employee.entity.CompanyBranch branch(Long id, Long locationId, boolean active) {
        return com.hrms.employee.entity.CompanyBranch.builder().id(id).code("B" + id).name("Branch " + id)
            .locationId(locationId).isActive(active).build();
    }

    private EmployeeJobDetails savedJobFor(JobDetailsRequest req) {
        when(jobDetailsRepository.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.empty());
        when(jobDetailsRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        // The save succeeds; the final re-read is not stubbed, so the call ends in NoSuchElementException, as above.
        assertThrows(java.util.NoSuchElementException.class, () -> service.assignJob(5L, req));
        var captor = org.mockito.ArgumentCaptor.forClass(EmployeeJobDetails.class);
        verify(jobDetailsRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void branchOfTheSameLocationIsStoredOnTheNewJobRow() {
        when(branchRepository.findById(40L)).thenReturn(Optional.of(branch(40L, 30L, true)));
        JobDetailsRequest req = request(LocalDate.now());
        req.setCompanyBranchId(40L);

        assertEquals(40L, savedJobFor(req).getCompanyBranchId());
    }

    @Test
    void newAssignmentWithoutABranch_keepsTheCurrentBranchWhenTheLocationIsUnchanged() {
        EmployeeJobDetails current = EmployeeJobDetails.builder().employeeId(5L).departmentId(10L).designationId(20L)
            .locationId(30L).companyBranchId(40L).effectiveFrom(LocalDate.now().minusDays(30)).build();
        when(jobDetailsRepository.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.of(current));
        when(jobDetailsRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThrows(java.util.NoSuchElementException.class, () -> service.assignJob(5L, request(LocalDate.now())));
        var captor = org.mockito.ArgumentCaptor.forClass(EmployeeJobDetails.class);
        verify(jobDetailsRepository).save(captor.capture());

        assertEquals(40L, captor.getValue().getCompanyBranchId());
    }

    @Test
    void newAssignmentInAnotherLocation_doesNotCarryTheOldBranchOver() {
        EmployeeJobDetails current = EmployeeJobDetails.builder().employeeId(5L).departmentId(10L).designationId(20L)
            .locationId(31L).companyBranchId(40L).effectiveFrom(LocalDate.now().minusDays(30)).build();
        when(jobDetailsRepository.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.of(current));
        when(jobDetailsRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThrows(java.util.NoSuchElementException.class, () -> service.assignJob(5L, request(LocalDate.now())));
        var captor = org.mockito.ArgumentCaptor.forClass(EmployeeJobDetails.class);
        verify(jobDetailsRepository).save(captor.capture());

        assertNull(captor.getValue().getCompanyBranchId());
    }

    @Test
    void noBranchStoresNull() {
        assertNull(savedJobFor(request(LocalDate.now())).getCompanyBranchId());
        verifyNoInteractions(branchRepository);
    }

    @Test
    void branchOfAnotherLocationIsRejected() {
        when(branchRepository.findById(40L)).thenReturn(Optional.of(branch(40L, 31L, true)));
        JobDetailsRequest req = request(LocalDate.now());
        req.setCompanyBranchId(40L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.assignJob(5L, req));
        assertEquals("BRANCH_LOCATION_MISMATCH", ex.getRuleCode());
        verify(jobDetailsRepository, never()).save(any());
    }

    @Test
    void branchWithoutALocationIsRejectedAsMismatch() {
        when(branchRepository.findById(40L)).thenReturn(Optional.of(branch(40L, null, true)));
        JobDetailsRequest req = request(LocalDate.now());
        req.setCompanyBranchId(40L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.assignJob(5L, req));
        assertEquals("BRANCH_LOCATION_MISMATCH", ex.getRuleCode());
    }

    @Test
    void inactiveBranchIsRejected() {
        when(branchRepository.findById(40L)).thenReturn(Optional.of(branch(40L, 30L, false)));
        JobDetailsRequest req = request(LocalDate.now());
        req.setCompanyBranchId(40L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.assignJob(5L, req));
        assertEquals("BRANCH_INACTIVE", ex.getRuleCode());
    }

    @Test
    void unknownBranchIsNotFound() {
        JobDetailsRequest req = request(LocalDate.now());
        req.setCompanyBranchId(404L);

        assertThrows(ResourceNotFoundException.class, () -> service.assignJob(5L, req));
    }

    @Test
    void changingTheBranchOnAnUpdateIsAStructuralChange() {
        EmployeeJobDetails current = EmployeeJobDetails.builder().employeeId(5L).departmentId(10L).designationId(20L)
            .locationId(30L).companyBranchId(40L).build();
        when(jobDetailsRepository.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.of(current));
        JobDetailsRequest req = request(LocalDate.now());
        req.setCompanyBranchId(41L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.updateCurrentJob(5L, req));
        assertEquals("STRUCTURAL_CHANGE_NOT_ALLOWED", ex.getRuleCode());
        verify(jobDetailsRepository, never()).save(any());
    }
}
