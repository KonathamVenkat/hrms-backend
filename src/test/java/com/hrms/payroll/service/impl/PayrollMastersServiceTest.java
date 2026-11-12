package com.hrms.payroll.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.payroll.dto.request.SalaryComponentRequest;
import com.hrms.payroll.dto.request.SalaryStructureItemRequest;
import com.hrms.payroll.dto.request.SalaryStructureRequest;
import com.hrms.payroll.entity.SalaryComponent;
import com.hrms.payroll.entity.SalaryStructure;
import com.hrms.payroll.enums.CalculationType;
import com.hrms.payroll.enums.ComponentType;
import com.hrms.payroll.repository.SalaryComponentRepository;
import com.hrms.payroll.repository.SalaryStructureItemRepository;
import com.hrms.payroll.repository.SalaryStructureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Payroll set-up: an edit needs the current version, every write is audited, and a component that feeds the pay breakdown
 * of employees cannot be changed in a way that rewrites it (the same rule structures already follow).
 */
@ExtendWith(MockitoExtension.class)
class PayrollMastersServiceTest {

    @Mock SalaryComponentRepository components;
    @Mock SalaryStructureRepository structures;
    @Mock SalaryStructureItemRepository items;
    @Mock AuditTrail audit;

    SalaryComponentServiceImpl componentService;
    SalaryStructureServiceImpl structureService;

    @BeforeEach
    void setUp() {
        componentService = new SalaryComponentServiceImpl(components, audit);
        structureService = new SalaryStructureServiceImpl(structures, items, components, audit);
    }

    private SalaryComponent basic() {
        return SalaryComponent.builder().componentId(5L).componentCode("BASIC").componentName("Basic salary")
            .componentNameAr("x").componentType(ComponentType.EARNING).calcType(CalculationType.FIXED)
            .defaultValue(BigDecimal.ZERO).isTaxable(0).isNssfApplicable(1).sortOrder(1).isActive(1).version(2L).build();
    }

    private SalaryComponentRequest componentRequest(String code, String name, CalculationType calc, Long version) {
        return new SalaryComponentRequest(code, name, "x", ComponentType.EARNING, calc, BigDecimal.ZERO,
            false, true, null, 1, version);
    }

    private void componentFound() {
        when(components.findByIdForUpdate(5L)).thenReturn(Optional.of(basic()));
    }

    private void componentSaves() {
        when(components.saveAndFlush(any(SalaryComponent.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void componentUpdate_withoutAVersion_isRefused() {
        componentFound();
        assertThrows(BadRequestException.class,
            () -> componentService.update(5L, componentRequest("BASIC", "Basic salary", CalculationType.FIXED, null)));
    }

    @Test
    void componentUpdate_withAStaleVersion_isAConflict() {
        componentFound();
        assertThrows(OptimisticLockingFailureException.class,
            () -> componentService.update(5L, componentRequest("BASIC", "Basic salary", CalculationType.FIXED, 1L)));
    }

    @Test
    void aComponentThatFeedsEmployeePay_cannotChangeHowItIsCalculated() {
        componentFound();
        when(components.countCurrentEmployeesByComponentId(5L)).thenReturn(3);

        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> componentService.update(5L,
            componentRequest("BASIC", "Basic salary", CalculationType.PERCENTAGE_OF_BASIC, 2L)));

        assertEquals("COMPONENT_IN_USE", e.getRuleCode());
        verify(components, never()).saveAndFlush(any());
    }

    @Test
    void aComponentThatFeedsEmployeePay_cannotChangeItsCode() {
        componentFound();
        when(components.countCurrentEmployeesByComponentId(5L)).thenReturn(1);

        assertThrows(BusinessRuleException.class, () -> componentService.update(5L,
            componentRequest("BASE", "Basic salary", CalculationType.FIXED, 2L)));
    }

    @Test
    void aComponentThatFeedsEmployeePay_canStillBeRenamed_withoutLookingForEmployees_andTheEditIsAudited() {
        componentFound();
        componentSaves();
        when(components.existsByComponentName("Basic pay")).thenReturn(false);

        componentService.update(5L, componentRequest("BASIC", "Basic pay", CalculationType.FIXED, 2L));

        verify(audit).record(eq("SALARY_COMPONENT_UPDATED"), eq("SALARY_COMPONENT"), eq(5L), anyString());
    }

    @Test
    void aComponentThatFeedsEmployeePay_cannotBeDeactivated() {
        componentFound();
        when(components.countCurrentEmployeesByComponentId(5L)).thenReturn(2);

        assertThrows(BusinessRuleException.class, () -> componentService.toggleActive(5L, false));
        verify(components, never()).save(any());
    }

    @Test
    void anUnusedComponent_canBeDeactivatedAndActivated_bothAudited() {
        componentFound();
        when(components.countCurrentEmployeesByComponentId(5L)).thenReturn(0);
        when(components.save(any(SalaryComponent.class))).thenAnswer(i -> i.getArgument(0));

        componentService.toggleActive(5L, false);
        componentService.toggleActive(5L, true);

        verify(audit).record("SALARY_COMPONENT_DEACTIVATED", "SALARY_COMPONENT", 5L, "BASIC");
        verify(audit).record("SALARY_COMPONENT_ACTIVATED", "SALARY_COMPONENT", 5L, "BASIC");
    }

    @Test
    void creatingAComponent_isAudited() {
        when(components.existsByComponentCode(anyString())).thenReturn(false);
        when(components.existsByComponentName(anyString())).thenReturn(false);
        when(components.findNextSequenceValue()).thenReturn(40L);
        when(components.save(any(SalaryComponent.class))).thenAnswer(i -> i.getArgument(0));

        componentService.create(componentRequest("HOUSING", "Housing", CalculationType.FIXED, null));

        verify(audit).record(eq("SALARY_COMPONENT_CREATED"), eq("SALARY_COMPONENT"), eq(40L), anyString());
    }

    // ---------------------------------------------------------------- structures

    private SalaryStructure standard() {
        return SalaryStructure.builder().structureId(9L).structureCode("STD").structureName("Standard")
            .isActive(1).version(4L).build();
    }

    private SalaryStructureRequest structureRequest(Long version) {
        return new SalaryStructureRequest("STD", "Standard", null,
            List.of(new SalaryStructureItemRequest(5L, null, BigDecimal.TEN, BigDecimal.ZERO, 1)), version);
    }

    @Test
    void structureUpdate_withoutAVersion_isRefused() {
        when(structures.findByIdForUpdate(9L)).thenReturn(Optional.of(standard()));
        assertThrows(BadRequestException.class, () -> structureService.update(9L, structureRequest(null)));
    }

    @Test
    void structureUpdate_withAStaleVersion_isAConflict() {
        when(structures.findByIdForUpdate(9L)).thenReturn(Optional.of(standard()));
        assertThrows(OptimisticLockingFailureException.class, () -> structureService.update(9L, structureRequest(3L)));
    }

    @Test
    void aStructureCannotHoldAnInactiveComponent() {
        when(structures.findByIdForUpdate(9L)).thenReturn(Optional.of(standard()));
        when(structures.countCurrentEmployeesByStructureId(9L)).thenReturn(0);
        SalaryComponent inactive = basic();
        inactive.setIsActive(0);
        when(components.findAllByIdForUpdate(any())).thenReturn(List.of(inactive));

        BusinessRuleException refused = assertThrows(BusinessRuleException.class,
            () -> structureService.update(9L, structureRequest(4L)));

        assertEquals("COMPONENT_INACTIVE", refused.getRuleCode());
        verify(items, never()).save(any());
    }

    @Test
    void theStructureAndItsComponentsAreLockedForTheEdit() {
        when(structures.findByIdForUpdate(9L)).thenReturn(Optional.of(standard()));
        when(structures.countCurrentEmployeesByStructureId(9L)).thenReturn(0);
        when(components.findAllByIdForUpdate(any())).thenReturn(List.of());

        assertThrows(BusinessRuleException.class, () -> structureService.update(9L, structureRequest(4L)));

        verify(structures).findByIdForUpdate(9L);
        verify(components).findAllByIdForUpdate(any());
    }

    @Test
    void structureToggle_isAudited() {
        when(structures.findByIdForUpdate(9L)).thenReturn(Optional.of(standard()));
        when(structures.countCurrentEmployeesByStructureId(9L)).thenReturn(0);
        when(structures.save(any(SalaryStructure.class))).thenAnswer(i -> i.getArgument(0));

        structureService.toggleActive(9L, false);

        verify(audit).record("SALARY_STRUCTURE_DEACTIVATED", "SALARY_STRUCTURE", 9L, "STD");
    }
}
