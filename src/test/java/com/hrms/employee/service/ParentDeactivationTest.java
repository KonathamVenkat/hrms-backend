package com.hrms.employee.service;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.employee.dto.request.DirectorateRequest;
import com.hrms.employee.entity.CompanyBranch;
import com.hrms.employee.entity.Directorate;
import com.hrms.employee.repository.CompanyBranchRepository;
import com.hrms.employee.repository.DepartmentRepository;
import com.hrms.employee.repository.DirectorateRepository;
import com.hrms.employee.service.impl.DirectorateServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A directorate cannot be switched off while active departments still hang on it. */
@ExtendWith(MockitoExtension.class)
class ParentDeactivationTest {

    @Mock DirectorateRepository directorates;
    @Mock CompanyBranchRepository companyBranches;
    @Mock DepartmentRepository departments;
    @Mock AuditTrail audit;
    @Mock EntityManager entityManager;

    DirectorateServiceImpl directorateService;

    @BeforeEach
    void setUp() {
        directorateService = new DirectorateServiceImpl(directorates, companyBranches, departments, audit, entityManager);
    }

    @Test
    void aDirectorateWithActiveDepartments_cannotBeDeactivated() {
        Directorate d = Directorate.builder().id(3L).code("DIR-001").companyBranchId(1L).isActive(true).build();
        when(directorates.findById(3L)).thenReturn(Optional.of(d));
        when(departments.existsByDirectorateIdAndIsActive(3L, true)).thenReturn(true);

        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> directorateService.deactivate(3L));

        assertEquals("MASTER_HAS_ACTIVE_CHILDREN", e.getRuleCode());
        verify(directorates, never()).save(any());
    }

    @Test
    void aDirectorateWithoutActiveDepartments_canBeDeactivated() {
        Directorate d = Directorate.builder().id(3L).code("DIR-001").companyBranchId(1L).isActive(true).build();
        when(directorates.findById(3L)).thenReturn(Optional.of(d));
        when(departments.existsByDirectorateIdAndIsActive(3L, true)).thenReturn(false);

        directorateService.deactivate(3L);

        verify(audit).record("DIRECTORATE_DEACTIVATED", "DIRECTORATE", 3L, "DIR-001");
    }

    @Test
    void savingADirectorateAsInactive_isRefusedWhileItHasActiveDepartments() {
        Directorate d = Directorate.builder().id(3L).code("DIR-001").companyBranchId(1L).isActive(true).version(2L).build();
        when(directorates.findById(3L)).thenReturn(Optional.of(d));
        when(directorates.existsByCodeIgnoreCaseAndIdNot("DIR-001", 3L)).thenReturn(false);
        when(companyBranches.findById(1L)).thenReturn(Optional.of(
            CompanyBranch.builder().id(1L).companyId(1L).code("B1").name("Branch").nameAr("x").isActive(true).build()));
        when(departments.existsByDirectorateIdAndIsActive(3L, true)).thenReturn(true);
        DirectorateRequest request = new DirectorateRequest();
        request.setCompanyBranchId(1L);
        request.setCode("DIR-001");
        request.setName("Operations");
        request.setNameAr("x");
        request.setIsActive(false);
        request.setVersion(2L);

        assertThrows(BusinessRuleException.class, () -> directorateService.update(3L, request));
        verify(directorates, never()).save(any());
    }
}
