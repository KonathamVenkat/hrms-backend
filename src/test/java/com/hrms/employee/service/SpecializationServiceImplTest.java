package com.hrms.employee.service;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.SpecializationRequest;
import com.hrms.employee.dto.response.MasterLookupResponse;
import com.hrms.employee.dto.response.SpecializationResponse;
import com.hrms.employee.entity.Specialization;
import com.hrms.employee.repository.SpecializationRepository;
import com.hrms.employee.service.impl.SpecializationServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpecializationServiceImplTest {

    @Mock SpecializationRepository repository;
    @Mock AuditTrail audit;
    @Mock EntityManager entityManager;

    SpecializationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SpecializationServiceImpl(repository, audit, entityManager);
    }

    private Specialization costAccounting() {
        return Specialization.builder().id(5L).code("COST_ACC").name("Cost accounting").nameAr("محاسبة تكاليف")
            .isActive(true).version(2L).build();
    }

    private SpecializationRequest request(String code, Long version) {
        return SpecializationRequest.builder().code(code).name(" Cost accounting ").nameAr(" محاسبة تكاليف ").version(version).build();
    }

    @Test
    void create_storesTheTrimmedUpperCaseCode_andAuditsIt() {
        when(repository.existsByCodeIgnoreCase("COST_ACC")).thenReturn(false);
        when(repository.save(any(Specialization.class))).thenAnswer(i -> {
            Specialization s = i.getArgument(0);
            s.setId(9L);
            return s;
        });

        SpecializationResponse out = service.create(request(" cost_acc ", null));

        assertEquals("COST_ACC", out.getCode());
        assertEquals("Cost accounting", out.getName());
        assertEquals("محاسبة تكاليف", out.getNameAr());
        assertTrue(out.getIsActive());
        verify(audit).record(eq("SPECIALIZATION_CREATED"), eq("SPECIALIZATION"), eq(9L), anyString());
    }

    @Test
    void create_refusesADuplicateCode() {
        when(repository.existsByCodeIgnoreCase("COST_ACC")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> service.create(request("cost_acc", null)));
        verify(repository, never()).save(any());
    }

    @Test
    void update_needsTheVersion_andRefusesAStaleOne() {
        when(repository.findById(5L)).thenReturn(Optional.of(costAccounting()));

        assertThrows(BadRequestException.class, () -> service.update(5L, request("COST_ACC", null)));
        assertThrows(OptimisticLockingFailureException.class, () -> service.update(5L, request("COST_ACC", 1L)));
        verify(repository, never()).save(any());
    }

    @Test
    void update_refusesACodeThatBelongsToAnotherRow() {
        when(repository.findById(5L)).thenReturn(Optional.of(costAccounting()));
        when(repository.existsByCodeIgnoreCaseAndIdNot("OTHER", 5L)).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> service.update(5L, request("other", 2L)));
    }

    @Test
    void update_changesTheRow_andAuditsBeforeAndAfter() {
        Specialization row = costAccounting();
        when(repository.findById(5L)).thenReturn(Optional.of(row));
        when(repository.save(row)).thenReturn(row);

        service.update(5L, request("cost_acc_2", 2L));

        assertEquals("COST_ACC_2", row.getCode());
        verify(audit).record(eq("SPECIALIZATION_UPDATED"), eq("SPECIALIZATION"), eq(5L), contains("->"));
    }

    @Test
    void unknownIdIsNotFound() {
        when(repository.findById(7L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getById(7L));
        assertThrows(ResourceNotFoundException.class, () -> service.deactivate(7L));
    }

    @Test
    void deactivateAndActivateToggleTheFlag() {
        Specialization row = costAccounting();
        when(repository.findById(5L)).thenReturn(Optional.of(row));

        service.deactivate(5L);
        assertFalse(row.getIsActive());
        verify(audit).record("SPECIALIZATION_DEACTIVATED", "SPECIALIZATION", 5L, "COST_ACC");

        service.activate(5L);
        assertTrue(row.getIsActive());
        verify(audit).record("SPECIALIZATION_ACTIVATED", "SPECIALIZATION", 5L, "COST_ACC");
    }

    @Test
    void delete_ofARowInUseIsReportedAsInUse_andNotAudited() {
        Specialization row = costAccounting();
        when(repository.findById(5L)).thenReturn(Optional.of(row));
        doThrow(new DataIntegrityViolationException("fk")).when(repository).flush();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.delete(5L));

        assertEquals("MASTER_IN_USE", ex.getRuleCode());
        verify(audit, never()).record(eq("SPECIALIZATION_DELETED"), anyString(), anyLong(), anyString());
    }

    @Test
    void delete_ofAnUnusedRowIsAudited() {
        when(repository.findById(5L)).thenReturn(Optional.of(costAccounting()));

        service.delete(5L);

        verify(repository).delete(any(Specialization.class));
        verify(audit).record(eq("SPECIALIZATION_DELETED"), eq("SPECIALIZATION"), eq(5L), anyString());
    }

    @Test
    void lookup_listsActiveSpecializationsOnly() {
        when(repository.findByIsActiveOrderByNameAsc(true)).thenReturn(List.of(costAccounting()));

        List<MasterLookupResponse> out = service.getActiveLookup();

        assertEquals(1, out.size());
        assertEquals("Cost accounting", out.get(0).getName());
        assertEquals(5L, out.get(0).getId());
    }
}
