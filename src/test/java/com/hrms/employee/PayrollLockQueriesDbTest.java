package com.hrms.employee;

import com.hrms.payroll.entity.SalaryComponent;
import com.hrms.payroll.entity.SalaryStructure;
import com.hrms.payroll.enums.CalculationType;
import com.hrms.payroll.enums.ComponentType;
import com.hrms.payroll.repository.SalaryComponentRepository;
import com.hrms.payroll.repository.SalaryStructureRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The row-lock queries on salary structures and components against a real Oracle: they must run
 * (FOR UPDATE together with ORDER BY and IN), return the rows, and return them in id order.
 */
@Transactional
class PayrollLockQueriesDbTest extends DatabaseTest {

    @Autowired SalaryComponentRepository components;
    @Autowired SalaryStructureRepository structures;
    @Autowired EntityManager em;

    private SalaryComponent component(String code) {
        return components.saveAndFlush(SalaryComponent.builder()
            .componentId(components.findNextSequenceValue())
            .componentCode(code).componentName("Lock " + code).componentNameAr("اختبار")
            .componentType(ComponentType.EARNING).calcType(CalculationType.FIXED).build());
    }

    @Test
    void lockingOneStructureReturnsIt() {
        SalaryStructure s = structures.saveAndFlush(SalaryStructure.builder()
            .structureId(structures.findNextSequenceValue())
            .structureCode("LOCK-S1").structureName("Lock structure").build());
        em.clear();
        assertTrue(structures.findByIdForUpdate(s.getStructureId()).isPresent());
        assertTrue(structures.findByIdForUpdate(-1L).isEmpty());
    }

    @Test
    void lockingOneComponentReturnsIt() {
        SalaryComponent c = component("LOCK_C1");
        em.clear();
        assertTrue(components.findByIdForUpdate(c.getComponentId()).isPresent());
        assertTrue(components.findByIdForUpdate(-1L).isEmpty());
    }

    @Test
    void lockingSeveralComponentsReturnsThemInIdOrderWhateverOrderTheyAreAskedFor() {
        long a = component("LOCK_A").getComponentId();
        long b = component("LOCK_B").getComponentId();
        long c = component("LOCK_C").getComponentId();
        em.clear();
        List<SalaryComponent> locked = components.findAllByIdForUpdate(Set.of(c, a, b, -1L));
        assertEquals(List.of(a, b, c), locked.stream().map(SalaryComponent::getComponentId).toList());
    }
}
