package com.hrms.employee.service;

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

    DesignationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DesignationServiceImpl(designationRepository, departmentRepository, jobDetailsRepository);
    }

    private DesignationRequest request() {
        return DesignationRequest.builder().code(" sr_swe ").title(" Senior Software Engineer ")
            .titleAr("  ").gradeLevel(" P3 ").departmentId(100L).build();
    }

    private Designation existing() {
        return Designation.builder().desigId(8L).code("SR_SWE").title("Senior Software Engineer")
            .departmentId(100L).isActive(1).build();
    }

    @Test
    void create_normalisesFieldsAndDefaultsToActive() {
        when(designationRepository.existsByCodeIgnoreCase("SR_SWE")).thenReturn(false);
        when(departmentRepository.existsById(100L)).thenReturn(true);
        when(departmentRepository.findById(100L)).thenReturn(Optional.of(
            Department.builder().deptId(100L).name("Information Technology").build()));
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
        when(departmentRepository.existsById(100L)).thenReturn(false);

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

        assertThrows(DuplicateResourceException.class, () -> service.updateDesignation(8L, request()));
    }

    @Test
    void getAll_attachesDepartmentNamesAndToleratesNoDepartment() {
        Designation withDept = existing();
        Designation noDept = Designation.builder().desigId(9L).code("X").title("X").isActive(0).build();
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

        assertEquals(0, d.getIsActive());
        assertNotNull(d.getUpdatedAt());
        verify(designationRepository).save(d);
    }

    @Test
    void delete_blockedWhileEmployeesUseIt() {
        when(designationRepository.findById(8L)).thenReturn(Optional.of(existing()));
        when(jobDetailsRepository.existsByDesignationId(8L)).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> service.deleteDesignation(8L));
        verify(designationRepository, never()).delete(any());
    }

    @Test
    void delete_removesUnusedDesignation() {
        Designation d = existing();
        when(designationRepository.findById(8L)).thenReturn(Optional.of(d));
        when(jobDetailsRepository.existsByDesignationId(8L)).thenReturn(false);

        service.deleteDesignation(8L);

        verify(designationRepository).delete(d);
    }
}
