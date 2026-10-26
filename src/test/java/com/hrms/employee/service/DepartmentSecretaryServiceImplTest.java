package com.hrms.employee.service;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.response.DepartmentSecretaryResponse;
import com.hrms.employee.entity.Department;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.entity.EmployeeJobDetails;
import com.hrms.employee.repository.DepartmentRepository;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.service.impl.DepartmentSecretaryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Department secretary rules: one per department, replaced atomically, never an inactive or departed person. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DepartmentSecretaryServiceImplTest {

    @Mock EmployeeRepository employeeRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock EmployeeJobDetailsRepository jobDetailsRepository;
    @Mock AuditTrail audit;

    DepartmentSecretaryServiceImpl service;
    Department dept;
    Employee employee;

    @BeforeEach
    void setUp() {
        service = new DepartmentSecretaryServiceImpl(employeeRepository, departmentRepository, jobDetailsRepository, audit,
            mock(org.springframework.context.ApplicationEventPublisher.class));
        dept = Department.builder().deptId(10L).code("IT").name("Information Technology").isActive(true).build();
        employee = mock(Employee.class);
        when(employee.getIsActive()).thenReturn(true);
        when(employeeRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(employee));
        when(employeeRepository.existsById(5L)).thenReturn(true);
        when(jobDetailsRepository.findByEmployeeIdAndIsCurrent(5L, 1))
            .thenReturn(Optional.of(EmployeeJobDetails.builder().employeeId(5L).departmentId(10L).build()));
        when(departmentRepository.findById(10L)).thenReturn(Optional.of(dept));
    }

    @Test
    void makingAnEmployeeSecretarySetsItAndAudits() {
        DepartmentSecretaryResponse r = service.set(5L, true);

        assertEquals(5L, dept.getSecretaryEmployeeId());
        assertTrue(r.secretary());
        verify(departmentRepository).saveAndFlush(dept);
        verify(audit).record("DEPARTMENT_SECRETARY_SET", "DEPARTMENT", 10L, "secretary=null -> 5");
    }

    @Test
    void anotherSecretaryIsReplaced() {
        dept.setSecretaryEmployeeId(9L);

        service.set(5L, true);

        assertEquals(5L, dept.getSecretaryEmployeeId());
        verify(audit).record("DEPARTMENT_SECRETARY_SET", "DEPARTMENT", 10L, "secretary=9 -> 5");
    }

    @Test
    void settingTheSameSecretaryTwiceChangesNothing() {
        dept.setSecretaryEmployeeId(5L);

        service.set(5L, true);

        verify(departmentRepository, never()).saveAndFlush(any());
        verify(audit, never()).record(anyString(), anyString(), any(), anyString());
    }

    @Test
    void clearingRemovesOnlyThisEmployeeAndAudits() {
        dept.setSecretaryEmployeeId(5L);

        service.set(5L, false);

        assertNull(dept.getSecretaryEmployeeId());
        verify(audit).record("DEPARTMENT_SECRETARY_CLEARED", "DEPARTMENT", 10L, "secretary=5 -> null");
    }

    @Test
    void clearingSomeoneWhoIsNotSecretaryLeavesTheRealOneAlone() {
        dept.setSecretaryEmployeeId(9L);

        service.set(5L, false);

        assertEquals(9L, dept.getSecretaryEmployeeId());
        verify(departmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void inactiveEmployeeCannotBecomeSecretary() {
        when(employee.getIsActive()).thenReturn(false);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.set(5L, true));
        assertEquals("EMP_INACTIVE", ex.getRuleCode());
        verify(departmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void inactiveDepartmentCannotGetASecretary() {
        dept.setIsActive(false);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.set(5L, true));
        assertEquals("DEPARTMENT_INACTIVE", ex.getRuleCode());
    }

    @Test
    void employeeWithoutACurrentJobIsNotFound() {
        when(jobDetailsRepository.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.set(5L, true));
    }

    @Test
    void staleDepartmentVersionSurfacesAsOptimisticLockFailure() {
        when(departmentRepository.saveAndFlush(dept)).thenThrow(new OptimisticLockingFailureException("stale"));

        assertThrows(OptimisticLockingFailureException.class, () -> service.set(5L, true));
        verify(audit, never()).record(anyString(), anyString(), any(), anyString());
    }

    @Test
    void getReportsTheCurrentSecretaryByName() {
        Employee secretary = mock(Employee.class);
        when(secretary.getFullName()).thenReturn("Jane Doe");
        dept.setSecretaryEmployeeId(9L);
        when(employeeRepository.findById(9L)).thenReturn(Optional.of(secretary));

        DepartmentSecretaryResponse r = service.get(5L);

        assertFalse(r.secretary());
        assertEquals(9L, r.currentSecretaryId());
        assertEquals("Jane Doe", r.currentSecretaryName());
    }

    @Test
    void movingDepartmentKeepsTheNewDepartmentAndClearsTheOthers() {
        Department old = Department.builder().deptId(11L).secretaryEmployeeId(5L).isActive(true).build();
        Department kept = Department.builder().deptId(10L).secretaryEmployeeId(5L).isActive(true).build();
        when(departmentRepository.findBySecretaryEmployeeId(5L)).thenReturn(List.of(old, kept));

        service.clearSecretaryOf(5L, 10L);

        assertNull(old.getSecretaryEmployeeId());
        assertEquals(5L, kept.getSecretaryEmployeeId());
        verify(audit).record("DEPARTMENT_SECRETARY_CLEARED", "DEPARTMENT", 11L, "secretary=5 -> null");
        verify(departmentRepository, never()).saveAndFlush(kept);
    }

    @Test
    void deactivationClearsEverySecretaryPost() {
        Department a = Department.builder().deptId(10L).secretaryEmployeeId(5L).isActive(true).build();
        Department b = Department.builder().deptId(11L).secretaryEmployeeId(5L).isActive(true).build();
        when(departmentRepository.findBySecretaryEmployeeId(5L)).thenReturn(List.of(a, b));

        service.clearSecretaryOf(5L, null);

        assertNull(a.getSecretaryEmployeeId());
        assertNull(b.getSecretaryEmployeeId());
        verify(audit).record("DEPARTMENT_SECRETARY_CLEARED", "DEPARTMENT", 10L, "secretary=5 -> null");
        verify(audit).record("DEPARTMENT_SECRETARY_CLEARED", "DEPARTMENT", 11L, "secretary=5 -> null");
    }
}
