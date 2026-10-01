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

    JobDetailsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new JobDetailsServiceImpl(jobDetailsRepository, employeeRepository,
            departmentRepository, designationRepository, locationRepository, shiftRepository);

        // Happy-path references: active employee 5, department 10, designation 20 in 10, location 30.
        when(employeeRepository.findByIdAndIsActive(5L, true)).thenReturn(Optional.of(mock(Employee.class)));
        Department dept = mock(Department.class);
        when(dept.getIsActive()).thenReturn(1);
        when(departmentRepository.findById(10L)).thenReturn(Optional.of(dept));
        Designation desig = mock(Designation.class);
        when(desig.getIsActive()).thenReturn(1);
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
        when(other.getIsActive()).thenReturn(1);
        when(other.getDepartmentId()).thenReturn(99L);
        when(designationRepository.findById(20L)).thenReturn(Optional.of(other));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.assignJob(5L, request(LocalDate.now())));
        assertEquals("DESIGNATION_DEPARTMENT_MISMATCH", ex.getRuleCode());
    }

    @Test
    void inactiveDepartmentIsRejected() {
        Department inactive = mock(Department.class);
        when(inactive.getIsActive()).thenReturn(0);
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
}
