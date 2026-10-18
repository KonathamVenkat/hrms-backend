package com.hrms.employee.service;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.DesignationRequest;
import com.hrms.employee.dto.response.DesignationResponse;
import com.hrms.employee.entity.Department;
import com.hrms.employee.entity.Designation;
import com.hrms.employee.repository.DepartmentRepository;
import com.hrms.employee.repository.DesignationRepository;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.service.impl.DesignationServiceImpl;
import jakarta.persistence.EntityManager;
import org.springframework.dao.OptimisticLockingFailureException;
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
class DesignationServiceImplTest {

    @Mock DesignationRepository designationRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock EmployeeJobDetailsRepository jobDetailsRepository;
    @Mock AuditTrail audit;
    @Mock EntityManager entityManager;

    DesignationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DesignationServiceImpl(designationRepository, departmentRepository, jobDetailsRepository, audit, entityManager);
    }

    private DesignationRequest request() {
        return DesignationRequest.builder().code(" sr_swe ").title(" Senior Software Engineer ")
            .titleAr("  ").gradeLevel(" P3 ").departmentId(100L).build();
    }

    private Designation existing() {
        return Designation.builder().desigId(8L).code("SR_SWE").title("Senior Software Engineer")
            .departmentId(100L).isActive(true).version(0L).build();
    }

    @Test
    void create_normalisesFieldsAndDefaultsToActive() {
        when(designationRepository.existsByCodeIgnoreCase("SR_SWE")).thenReturn(false);
        when(departmentRepository.findById(100L)).thenReturn(Optional.of(
            Department.builder().deptId(100L).name("Information Technology").isActive(true).build()));
        when(designationRepository.save(any(Designation.class))).thenAnswer(i -> i.getArgument(0));

        DesignationResponse out = service.createDesignation(request());

        assertEquals("SR_SWE", out.getCode());
        assertEquals("Senior Software Engineer", out.getTitle());
        assertNull(out.getTitleAr(), "a blank Arabic title is stored as null");
        assertEquals("P3", out.getGradeLevel());
        assertEquals("Information Technology", out.getDepartmentName());
        assertTrue(out.getIsActive());
    }

    @Test
    void create_rejectsDuplicateCode() {
        when(designationRepository.existsByCodeIgnoreCase("SR_SWE")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> service.createDesignation(request()));
        verify(designationRepository, never()).save(any());
    }

    @Test
    void create_rejectsUnknownDepartment() {
        when(designationRepository.existsByCodeIgnoreCase("SR_SWE")).thenReturn(false);
        when(departmentRepository.findById(100L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.createDesignation(request()));
        verify(designationRepository, never()).save(any());
    }

    @Test
    void create_allowsNoDepartment() {
        DesignationRequest r = request();
        r.setDepartmentId(null);
        when(designationRepository.existsByCodeIgnoreCase("SR_SWE")).thenReturn(false);
        when(designationRepository.save(any(Designation.class))).thenAnswer(i -> i.getArgument(0));

        DesignationResponse out = service.createDesignation(r);

        assertNull(out.getDepartmentId());
        assertNull(out.getDepartmentName());
    }

    @Test
    void update_rejectsAnotherDesignationsCode() {
        when(designationRepository.findById(8L)).thenReturn(Optional.of(existing()));
        when(designationRepository.existsByCodeIgnoreCaseAndDesigIdNot("SR_SWE", 8L)).thenReturn(true);
        DesignationRequest r = request();
        r.setVersion(0L);

        assertThrows(DuplicateResourceException.class, () -> service.updateDesignation(8L, r));
    }

    @Test
    void getAll_attachesDepartmentNamesAndToleratesNoDepartment() {
        Designation withDept = existing();
        Designation noDept = Designation.builder().desigId(9L).code("X").title("X").isActive(false).build();
        when(departmentRepository.findAll()).thenReturn(List.of(
            Department.builder().deptId(100L).name("Information Technology").build()));
        when(designationRepository.findAllByOrderByTitleAsc()).thenReturn(List.of(withDept, noDept));

        List<DesignationResponse> out = service.getAllDesignations();

        assertEquals("Information Technology", out.get(0).getDepartmentName());
        assertNull(out.get(1).getDepartmentName());
        assertFalse(out.get(1).getIsActive());
    }

    @Test
    void deactivate_setsInactiveAndStampsUpdate() {
        Designation d = existing();
        when(designationRepository.findById(8L)).thenReturn(Optional.of(d));

        service.deactivateDesignation(8L);

        assertFalse(d.getIsActive());
        assertNotNull(d.getUpdatedAt());
        verify(designationRepository).save(d);
        verify(audit).record("DESIGNATION_DEACTIVATED", "DESIGNATION", 8L, d.getCode());
    }

    @Test
    void delete_blockedWhileEmployeesUseIt() {
        when(designationRepository.findById(8L)).thenReturn(Optional.of(existing()));
        when(jobDetailsRepository.existsByDesignationId(8L)).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> service.deleteDesignation(8L));
        verify(designationRepository, never()).delete(any());
        verifyNoInteractions(audit);
    }

    @Test
    void delete_removesUnusedDesignation() {
        Designation d = existing();
        when(designationRepository.findById(8L)).thenReturn(Optional.of(d));
        when(jobDetailsRepository.existsByDesignationId(8L)).thenReturn(false);

        service.deleteDesignation(8L);

        verify(designationRepository).delete(d);
        verify(audit).record(eq("DESIGNATION_DELETED"), eq("DESIGNATION"), eq(8L), anyString());
    }

    @Test
    void update_rejectsStaleVersion() {
        Designation d = existing();
        d.setVersion(3L);
        when(designationRepository.findById(8L)).thenReturn(Optional.of(d));
        DesignationRequest r = request();
        r.setVersion(2L);

        assertThrows(OptimisticLockingFailureException.class, () -> service.updateDesignation(8L, r));
        verify(designationRepository, never()).save(any());
    }

    @Test
    void lookupByDepartment_returnsActiveDesignationsOfThatDepartment() {
        when(designationRepository.findByDepartmentIdAndIsActiveOrderByTitleAsc(100L, true))
            .thenReturn(java.util.List.of(existing()));

        var out = service.getActiveLookupByDepartment(100L);

        assertEquals(1, out.size());
        assertEquals(100L, out.get(0).getDepartmentId());
    }

    @Test
    void update_requiresVersion() {
        when(designationRepository.findById(8L)).thenReturn(Optional.of(existing()));

        assertThrows(BadRequestException.class, () -> service.updateDesignation(8L, request()));
        verify(designationRepository, never()).save(any());
    }

    @Test
    void create_rejectsInactiveDepartment() {
        when(designationRepository.existsByCodeIgnoreCase("SR_SWE")).thenReturn(false);
        when(departmentRepository.findById(100L)).thenReturn(Optional.of(
            Department.builder().deptId(100L).code("IT").isActive(false).build()));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.createDesignation(request()));
        assertEquals("DEPARTMENT_INACTIVE", ex.getRuleCode());
    }

    @Test
    void activate_rejectsInactiveDepartment() {
        Designation d = existing();
        d.setIsActive(false);
        when(designationRepository.findById(8L)).thenReturn(Optional.of(d));
        when(departmentRepository.findById(100L)).thenReturn(Optional.of(
            Department.builder().deptId(100L).code("IT").isActive(false).build()));

        assertThrows(BusinessRuleException.class, () -> service.activateDesignation(8L));
        verify(designationRepository, never()).save(any());
    }

    @Test
    void update_blocksMovingAnInUseDesignationToAnotherDepartment() {
        Designation d = existing();
        d.setVersion(1L);
        when(designationRepository.findById(8L)).thenReturn(Optional.of(d));
        when(departmentRepository.findById(200L)).thenReturn(Optional.of(
            Department.builder().deptId(200L).code("HR").isActive(true).build()));
        when(jobDetailsRepository.existsByDesignationId(8L)).thenReturn(true);
        DesignationRequest r = request();
        r.setDepartmentId(200L);
        r.setVersion(1L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.updateDesignation(8L, r));
        assertEquals("DESIGNATION_IN_USE", ex.getRuleCode());
        verify(designationRepository, never()).save(any());
    }
}
