package com.hrms.employee.service;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.DepartmentRequest;
import com.hrms.employee.dto.response.DepartmentResponse;
import com.hrms.employee.entity.Department;
import com.hrms.employee.repository.DepartmentRepository;
import com.hrms.employee.repository.DesignationRepository;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.service.impl.DepartmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceImplTest {

    @Mock DepartmentRepository departmentRepository;
    @Mock DesignationRepository designationRepository;
    @Mock EmployeeJobDetailsRepository jobDetailsRepository;
    @Mock EmployeeRepository employeeRepository;

    DepartmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DepartmentServiceImpl(departmentRepository, designationRepository,
            jobDetailsRepository, employeeRepository);
    }

    private DepartmentRequest request() {
        return DepartmentRequest.builder().code(" it ").name(" Information Technology ")
            .nameAr(" تقنية المعلومات ").build();
    }

    private Department existing() {
        return Department.builder().deptId(100L).code("IT").name("Information Technology")
            .nameAr("x").isActive(1).build();
    }

    @Test
    void create_normalisesCodeAndDefaultsToActive() {
        when(departmentRepository.existsByCodeIgnoreCase("IT")).thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenAnswer(i -> i.getArgument(0));

        DepartmentResponse out = service.createDepartment(request());

        assertEquals("IT", out.getCode());
        assertEquals("Information Technology", out.getName());
        assertTrue(out.getIsActive());
    }

    @Test
    void create_rejectsDuplicateCode() {
        when(departmentRepository.existsByCodeIgnoreCase("IT")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> service.createDepartment(request()));
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void create_rejectsUnknownHeadEmployee() {
        DepartmentRequest r = request();
        r.setHeadEmployeeId(999L);
        when(departmentRepository.existsByCodeIgnoreCase("IT")).thenReturn(false);
        when(employeeRepository.existsById(999L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.createDepartment(r));
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void update_allowsOwnCodeButRejectsAnotherDepartmentsCode() {
        when(departmentRepository.findById(100L)).thenReturn(Optional.of(existing()));
        when(departmentRepository.existsByCodeIgnoreCaseAndDeptIdNot("IT", 100L)).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> service.updateDepartment(100L, request()));
    }

    @Test
    void deactivate_setsInactiveAndStampsUpdate() {
        Department d = existing();
        when(departmentRepository.findById(100L)).thenReturn(Optional.of(d));

        service.deactivateDepartment(100L);

        assertEquals(0, d.getIsActive());
        assertNotNull(d.getUpdatedAt());
        verify(departmentRepository).save(d);
    }

    @Test
    void delete_blockedWhileDesignationsUseIt() {
        when(departmentRepository.findById(100L)).thenReturn(Optional.of(existing()));
        when(designationRepository.existsByDepartmentId(100L)).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> service.deleteDepartment(100L));
        verify(departmentRepository, never()).delete(any());
    }

    @Test
    void delete_blockedWhileEmployeesUseIt() {
        when(departmentRepository.findById(100L)).thenReturn(Optional.of(existing()));
        when(designationRepository.existsByDepartmentId(100L)).thenReturn(false);
        when(jobDetailsRepository.existsByDepartmentId(100L)).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> service.deleteDepartment(100L));
        verify(departmentRepository, never()).delete(any());
    }

    @Test
    void delete_removesUnusedDepartment() {
        Department d = existing();
        when(departmentRepository.findById(100L)).thenReturn(Optional.of(d));
        when(designationRepository.existsByDepartmentId(100L)).thenReturn(false);
        when(jobDetailsRepository.existsByDepartmentId(100L)).thenReturn(false);

        service.deleteDepartment(100L);

        verify(departmentRepository).delete(d);
    }

    @Test
    void delete_reportsInUseWhenForeignKeyFiresAtFlush() {
        Department d = existing();
        when(departmentRepository.findById(100L)).thenReturn(Optional.of(d));
        when(designationRepository.existsByDepartmentId(100L)).thenReturn(false);
        when(jobDetailsRepository.existsByDepartmentId(100L)).thenReturn(false);
        doThrow(new DataIntegrityViolationException("FK")).when(departmentRepository).flush();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.deleteDepartment(100L));
        assertEquals("DEPARTMENT_IN_USE", ex.getRuleCode());
    }
}
