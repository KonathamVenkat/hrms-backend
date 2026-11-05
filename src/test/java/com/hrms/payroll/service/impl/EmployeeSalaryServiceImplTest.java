package com.hrms.payroll.service.impl;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.DepartmentRepository;
import com.hrms.employee.repository.DesignationRepository;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.payroll.dto.request.EmployeeSalaryRequest;
import com.hrms.payroll.entity.EmployeeSalary;
import com.hrms.payroll.entity.SalaryStructure;
import com.hrms.payroll.repository.EmployeeSalaryRepository;
import com.hrms.payroll.repository.SalaryComponentRepository;
import com.hrms.payroll.repository.SalaryStructureItemRepository;
import com.hrms.payroll.repository.SalaryStructureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Assigning a salary: serialised per employee, and the record names the person who made it. */
class EmployeeSalaryServiceImplTest {

    EmployeeSalaryRepository      salaries    = mock(EmployeeSalaryRepository.class);
    SalaryStructureRepository     structures  = mock(SalaryStructureRepository.class);
    SalaryStructureItemRepository items       = mock(SalaryStructureItemRepository.class);
    SalaryComponentRepository     components  = mock(SalaryComponentRepository.class);
    EmployeeRepository            employees   = mock(EmployeeRepository.class);
    EmployeeJobDetailsRepository  jobs        = mock(EmployeeJobDetailsRepository.class);
    DepartmentRepository          departments = mock(DepartmentRepository.class);
    DesignationRepository         designations = mock(DesignationRepository.class);
    EmployeeAccessGuard           guard       = mock(EmployeeAccessGuard.class);
    AuditTrail                    audit       = mock(AuditTrail.class);
    EmployeeSalaryServiceImpl     service;

    @BeforeEach
    void setUp() {
        service = new EmployeeSalaryServiceImpl(salaries, structures, items, components, employees, jobs,
                departments, designations, guard, audit);
        Employee employee = new Employee();
        employee.setId(5L);
        employee.setEmployeeCode("EMP-5");
        employee.setFirstName("Sara");
        employee.setLastName("Khan");
        when(employees.findByIdForUpdate(5L)).thenReturn(Optional.of(employee));
        SalaryStructure structure = mock(SalaryStructure.class);
        when(structure.getIsActive()).thenReturn(1);
        when(structure.getStructureId()).thenReturn(3L);
        when(structure.getStructureName()).thenReturn("Standard");
        when(structures.findById(3L)).thenReturn(Optional.of(structure));
        when(items.findByStructureIdAndIsActiveOrderBySortOrderAsc(3L, 1)).thenReturn(List.of());
        when(salaries.findNextSequenceValue()).thenReturn(900L);
        when(salaries.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private EmployeeSalaryRequest request() {
        return new EmployeeSalaryRequest(5L, 3L, new BigDecimal("1500.00"), LocalDate.now().toString(), null);
    }

    @Test
    void theEmployeeRowIsLockedBeforeTheCurrentRecordIsClosedAndTheNewOneInserted() {
        EmployeeSalary previous = EmployeeSalary.builder().empSalaryId(1L).employeeId(5L)
                .effectiveFrom(LocalDate.now().minusDays(30)).isCurrent(1).build();
        when(salaries.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.of(previous));

        service.assign(request());

        InOrder order = inOrder(employees, salaries);
        order.verify(employees).findByIdForUpdate(5L);
        order.verify(salaries).deactivateCurrent(5L);
        order.verify(salaries).save(any());
    }

    @Test
    void theRecordIsCreatedByWhoeverAssignedItNotByTheEmployeeItBelongsTo() {
        when(salaries.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.empty());

        service.assign(request());

        ArgumentCaptor<EmployeeSalary> saved = ArgumentCaptor.forClass(EmployeeSalary.class);
        verify(salaries).save(saved.capture());
        assertNotEquals("EMP-5", saved.getValue().getCreatedBy());
        assertEquals(new BigDecimal("1500.00"), saved.getValue().getBasicSalary());
    }

    @Test
    void aSalaryIsReadByItsOwnerOrAnHrAdminAndByNobodyElse() {
        EmployeeSalary current = EmployeeSalary.builder().empSalaryId(1L).employeeId(5L).structureId(3L)
                .basicSalary(new BigDecimal("1500.00")).grossSalary(new BigDecimal("1500.00"))
                .netSalary(new BigDecimal("1500.00")).currency("SSP").effectiveFrom(LocalDate.now()).isCurrent(1).build();
        when(salaries.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.of(current));
        Employee owner = new Employee();
        owner.setId(5L);
        owner.setEmployeeCode("EMP-5");
        owner.setFirstName("Sara");
        owner.setLastName("Khan");
        when(employees.findById(5L)).thenReturn(Optional.of(owner));

        // an HR_MANAGER (or anyone else) who is not the owner and not an HR_ADMIN
        when(guard.isSelfOrAdmin(5L)).thenReturn(false);
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.getCurrentSalary(5L));

        // the owner or an HR_ADMIN
        when(guard.isSelfOrAdmin(5L)).thenReturn(true);
        assertEquals(new BigDecimal("1500.00"), service.getCurrentSalary(5L).basicSalary());
    }

    @Test
    void anUnknownEmployeeIsNotFoundAndNothingIsWritten() {
        when(employees.findByIdForUpdate(6L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.assign(
                new EmployeeSalaryRequest(6L, 3L, new BigDecimal("1500.00"), LocalDate.now().toString(), null)));
        verify(salaries, never()).save(any());
        verify(salaries, never()).deactivateCurrent(any());
    }
}
