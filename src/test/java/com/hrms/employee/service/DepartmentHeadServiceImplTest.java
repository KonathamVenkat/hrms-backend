package com.hrms.employee.service;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.response.DepartmentHeadResponse;
import com.hrms.employee.entity.Department;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.entity.EmployeeJobDetails;
import com.hrms.employee.repository.DepartmentRepository;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.service.impl.DepartmentHeadServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Department head rules: one head per department, replaced atomically, never an inactive or departed person. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DepartmentHeadServiceImplTest {

    @Mock EmployeeRepository employeeRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock EmployeeJobDetailsRepository jobDetailsRepository;
    @Mock AuditTrail audit;

    DepartmentHeadServiceImpl service;
    Department dept;
    Employee employee;

    @BeforeEach
    void setUp() {
        service = new DepartmentHeadServiceImpl(employeeRepository, departmentRepository, jobDetailsRepository, audit);
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
    void makingAnEmployeeHeadSetsTheDepartmentHeadAndAudits() {
        DepartmentHeadResponse r = service.set(5L, true);

        assertEquals(5L, dept.getHeadEmployeeId());
        assertTrue(r.head());
        verify(departmentRepository).saveAndFlush(dept);
        verify(audit).record("DEPARTMENT_HEAD_SET", "DEPARTMENT", 10L, "head=null -> 5");
    }

    @Test
    void anotherHeadIsReplaced() {
        dept.setHeadEmployeeId(9L);

        service.set(5L, true);

        assertEquals(5L, dept.getHeadEmployeeId());
        verify(audit).record("DEPARTMENT_HEAD_SET", "DEPARTMENT", 10L, "head=9 -> 5");
    }

    @Test
    void settingTheSameHeadTwiceChangesNothing() {
        dept.setHeadEmployeeId(5L);

        service.set(5L, true);

        verify(departmentRepository, never()).saveAndFlush(any());
        verify(audit, never()).record(anyString(), anyString(), any(), anyString());
    }

    @Test
    void clearingRemovesOnlyThisEmployeeAsHead() {
        dept.setHeadEmployeeId(5L);

        service.set(5L, false);

        assertNull(dept.getHeadEmployeeId());
        verify(audit).record("DEPARTMENT_HEAD_CLEARED", "DEPARTMENT", 10L, "head=5 -> null");
    }

    @Test
    void clearingSomeoneWhoIsNotHeadLeavesTheRealHeadAlone() {
        dept.setHeadEmployeeId(9L);

        service.set(5L, false);

        assertEquals(9L, dept.getHeadEmployeeId());
        verify(departmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void inactiveEmployeeCannotBecomeHead() {
        when(employee.getIsActive()).thenReturn(false);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.set(5L, true));
        assertEquals("EMP_INACTIVE", ex.getRuleCode());
        verify(departmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void inactiveDepartmentCannotGetAHead() {
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
    void unknownEmployeeIsNotFound() {
        when(employeeRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.set(99L, true));
    }

    @Test
    void employeeRowIsLockedBeforeTheDepartmentIsRead() {
        service.set(5L, true);

        var order = inOrder(employeeRepository, departmentRepository);
        order.verify(employeeRepository).findByIdForUpdate(5L);
        order.verify(departmentRepository).findById(10L);
    }

    @Test
    void getReportsTheCurrentHeadByName() {
        Employee head = mock(Employee.class);
        when(head.getFullName()).thenReturn("Jane Doe");
        dept.setHeadEmployeeId(9L);
        when(employeeRepository.findById(9L)).thenReturn(Optional.of(head));

        DepartmentHeadResponse r = service.get(5L);

        assertFalse(r.head());
        assertEquals(9L, r.currentHeadId());
        assertEquals("Jane Doe", r.currentHeadName());
    }

    @Test
    void movingDepartmentClearsHeadshipOfEveryOtherDepartment() {
        Department old = Department.builder().deptId(11L).headEmployeeId(5L).isActive(true).build();
        Department kept = Department.builder().deptId(10L).headEmployeeId(5L).isActive(true).build();
        when(departmentRepository.findByHeadEmployeeId(5L)).thenReturn(List.of(old, kept));

        service.clearHeadOf(5L, 10L);

        assertNull(old.getHeadEmployeeId());
        assertEquals(5L, kept.getHeadEmployeeId());
    }

    @Test
    void deactivationClearsEveryHeadship() {
        Department a = Department.builder().deptId(10L).headEmployeeId(5L).isActive(true).build();
        when(departmentRepository.findByHeadEmployeeId(5L)).thenReturn(List.of(a));

        service.clearHeadOf(5L, null);

        assertNull(a.getHeadEmployeeId());
    }
}
