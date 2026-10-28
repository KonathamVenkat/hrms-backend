package com.hrms.employee.service;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.employee.dto.response.MasterLookupResponse;
import com.hrms.employee.entity.Profession;
import com.hrms.employee.repository.DesignationRepository;
import com.hrms.employee.repository.ProfessionRepository;
import com.hrms.employee.service.impl.ProfessionServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfessionServiceImplTest {

    @Mock ProfessionRepository repository;
    @Mock DesignationRepository designationRepository;
    @Mock AuditTrail audit;
    @Mock EntityManager entityManager;

    ProfessionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProfessionServiceImpl(repository, designationRepository, audit, entityManager);
    }

    private Profession accountant() {
        return Profession.builder().id(5L).code("ACC").name("Accountant").nameAr("x").isActive(true).version(0L).build();
    }

    @Test
    void deactivate_isRefusedWhileActiveDesignationsUseTheProfession() {
        when(repository.findById(5L)).thenReturn(Optional.of(accountant()));
        when(designationRepository.existsByProfessionIdAndIsActive(5L, true)).thenReturn(true);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.deactivate(5L));

        assertEquals("PROFESSION_IN_USE", ex.getRuleCode());
        verify(repository, never()).save(any());
    }

    @Test
    void deactivate_worksWhenNoActiveDesignationUsesIt_andActivateNeverChecks() {
        Profession p = accountant();
        when(repository.findById(5L)).thenReturn(Optional.of(p));
        when(designationRepository.existsByProfessionIdAndIsActive(5L, true)).thenReturn(false);

        service.deactivate(5L);
        assertFalse(p.getIsActive());

        service.activate(5L);
        assertTrue(p.getIsActive());
        verify(designationRepository, times(1)).existsByProfessionIdAndIsActive(5L, true);
    }

    @Test
    void lookup_listsActiveProfessionsOnly() {
        when(repository.findByIsActiveOrderByNameAsc(true)).thenReturn(List.of(accountant()));

        List<MasterLookupResponse> out = service.getActiveLookup();

        assertEquals(1, out.size());
        assertEquals("Accountant", out.get(0).getName());
        assertEquals(5L, out.get(0).getId());
    }
}
