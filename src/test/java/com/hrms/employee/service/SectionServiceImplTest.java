package com.hrms.employee.service;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.SectionRequest;
import com.hrms.employee.entity.Department;
import com.hrms.employee.entity.Section;
import com.hrms.employee.repository.DepartmentRepository;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.repository.SectionRepository;
import com.hrms.employee.service.impl.SectionServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SectionServiceImplTest {

    @Mock SectionRepository repository;
    @Mock DepartmentRepository departmentRepository;
    @Mock EmployeeJobDetailsRepository jobDetailsRepository;
    @Mock AuditTrail audit;
    @Mock EntityManager entityManager;

    SectionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SectionServiceImpl(repository, departmentRepository, jobDetailsRepository, audit, entityManager);
    }

    private SectionRequest request(Long departmentId) {
        return SectionRequest.builder().departmentId(departmentId).code(" sec-1 ").name(" Geology ").nameAr(" x ")
            .version(0L).build();
    }

    private Section existing() {
        return Section.builder().id(5L).departmentId(10L).code("SEC-1").name("Geology").nameAr("x")
            .isActive(true).version(0L).build();
    }

    private void department(Long id, boolean active) {
        when(departmentRepository.findById(id)).thenReturn(
            Optional.of(Department.builder().deptId(id).code("D" + id).isActive(active).build()));
    }

    @Test
    void createTrimsAndUppercasesTheCode() {
        department(10L, true);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.create(request(10L));

        var saved = org.mockito.ArgumentCaptor.forClass(Section.class);
        verify(repository).save(saved.capture());
        assertEquals("SEC-1", saved.getValue().getCode());
        assertEquals("Geology", saved.getValue().getName());
    }

    @Test
    void createNeedsAnExistingActiveDepartment() {
        when(departmentRepository.findById(11L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.create(request(11L)));

        department(12L, false);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.create(request(12L)));
        assertEquals("PARENT_INACTIVE", ex.getRuleCode());
        verify(repository, never()).save(any());
    }

    @Test
    void duplicateCodeIsRejected() {
        when(repository.existsByCodeIgnoreCase("SEC-1")).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () -> service.create(request(10L)));
    }

    @Test
    void updateNeedsTheVersion() {
        when(repository.findById(5L)).thenReturn(Optional.of(existing()));
        SectionRequest req = request(10L);
        req.setVersion(null);
        assertThrows(BadRequestException.class, () -> service.update(5L, req));
    }

    @Test
    void aSectionInUseCannotMoveToAnotherDepartment() {
        when(repository.findById(5L)).thenReturn(Optional.of(existing()));
        department(11L, true);
        when(jobDetailsRepository.existsBySectionId(5L)).thenReturn(true);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.update(5L, request(11L)));
        assertEquals("SECTION_IN_USE", ex.getRuleCode());
        verify(repository, never()).save(any());
    }

    @Test
    void aSectionInUseCannotBeDeleted() {
        when(repository.findById(5L)).thenReturn(Optional.of(existing()));
        when(jobDetailsRepository.existsBySectionId(5L)).thenReturn(true);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.delete(5L));
        assertEquals("MASTER_IN_USE", ex.getRuleCode());
        verify(repository, never()).delete(any());
    }

    @Test
    void activatingNeedsAnActiveDepartment() {
        when(repository.findById(5L)).thenReturn(Optional.of(existing()));
        department(10L, false);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.activate(5L));
        assertEquals("PARENT_INACTIVE", ex.getRuleCode());
    }

    @Test
    void lookupListsOnlyTheActiveSectionsOfTheDepartment() {
        when(repository.findByDepartmentIdAndIsActiveOrderByNameAsc(10L, true)).thenReturn(List.of(existing()));

        var lookup = service.getActiveLookupByDepartment(10L);

        assertEquals(1, lookup.size());
        assertEquals("SEC-1", lookup.get(0).getCode());
        assertEquals(10L, lookup.get(0).getDepartmentId());
    }
}
