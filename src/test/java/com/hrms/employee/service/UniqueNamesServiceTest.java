package com.hrms.employee.service;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.employee.dto.request.DesignationRequest;
import com.hrms.employee.dto.request.ReligionRequest;
import com.hrms.employee.dto.request.WilayaRequest;
import com.hrms.employee.entity.Designation;
import com.hrms.employee.entity.Religion;
import com.hrms.employee.repository.DepartmentRepository;
import com.hrms.employee.repository.DesignationRepository;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.repository.ProfessionRepository;
import com.hrms.employee.repository.ProvinceRepository;
import com.hrms.employee.repository.RegionRepository;
import com.hrms.employee.repository.ReligionRepository;
import com.hrms.employee.repository.WilayaRepository;
import com.hrms.employee.service.impl.DesignationServiceImpl;
import com.hrms.employee.service.impl.ReligionServiceImpl;
import com.hrms.employee.service.impl.WilayaServiceImpl;
import jakarta.persistence.EntityManager;
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

/** A name is unique like a code: overall for flat masters, inside the parent for the others (one example of each). */
@ExtendWith(MockitoExtension.class)
class UniqueNamesServiceTest {

    @Mock ReligionRepository religions;
    @Mock WilayaRepository wilayas;
    @Mock RegionRepository regions;
    @Mock ProvinceRepository provinces;
    @Mock DesignationRepository designations;
    @Mock DepartmentRepository departments;
    @Mock EmployeeJobDetailsRepository jobDetails;
    @Mock ProfessionRepository professions;
    @Mock AuditTrail audit;
    @Mock EntityManager entityManager;

    private ReligionRequest religion(Long version) {
        return ReligionRequest.builder().code("ISL").name(" Islam ").nameAr("x").version(version).build();
    }

    @Test
    void aFlatMasterRefusesANameThatExistsInAnyCase() {
        ReligionServiceImpl service = new ReligionServiceImpl(religions, audit, entityManager);
        when(religions.existsByCodeIgnoreCase("ISL")).thenReturn(false);
        when(religions.existsByNameIgnoreCase("Islam")).thenReturn(true);

        DuplicateResourceException e = assertThrows(DuplicateResourceException.class, () -> service.create(religion(null)));

        assertEquals(true, e.getMessage().contains("name"));
        verify(religions, never()).save(any());
    }

    @Test
    void aFlatMasterRefusesRenamingToAnotherRowsName() {
        ReligionServiceImpl service = new ReligionServiceImpl(religions, audit, entityManager);
        when(religions.findById(3L)).thenReturn(Optional.of(Religion.builder().id(3L).code("ISL").name("Old").version(2L).build()));
        when(religions.existsByCodeIgnoreCaseAndIdNot("ISL", 3L)).thenReturn(false);
        when(religions.existsByNameIgnoreCaseAndIdNot("Islam", 3L)).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> service.update(3L, religion(2L)));
        verify(religions, never()).save(any());
    }

    @Test
    void aParentedMasterChecksTheNameInsideTheGivenParentOnly() {
        WilayaServiceImpl service = new WilayaServiceImpl(wilayas, regions, provinces, audit, entityManager);
        WilayaRequest request = WilayaRequest.builder().regionId(7L).code("CES").name("Central").nameAr("x").build();
        when(wilayas.existsByCodeIgnoreCase("CES")).thenReturn(false);
        when(wilayas.existsByNameIgnoreCaseAndRegionId("Central", 7L)).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> service.create(request));
        verify(wilayas).existsByNameIgnoreCaseAndRegionId("Central", 7L);
    }

    @Test
    void aDesignationWithoutADepartmentIsCheckedAgainstOtherDesignationsWithoutOne() {
        DesignationServiceImpl service = new DesignationServiceImpl(designations, departments, jobDetails, professions, audit, entityManager);
        DesignationRequest request = DesignationRequest.builder().code("SWE").title(" Software Engineer ").build();
        when(designations.existsByCodeIgnoreCase("SWE")).thenReturn(false);
        when(designations.existsByTitleIgnoreCaseAndDepartmentId("Software Engineer", null)).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> service.createDesignation(request));
        verify(designations, never()).save(any(Designation.class));
    }

    @Test
    void renamingADesignationIgnoresItself() {
        DesignationServiceImpl service = new DesignationServiceImpl(designations, departments, jobDetails, professions, audit, entityManager);
        Designation existing = Designation.builder().desigId(8L).code("SWE").title("Old").departmentId(100L).isActive(true).version(1L).build();
        DesignationRequest request = DesignationRequest.builder().code("SWE").title("Software Engineer").departmentId(100L).version(1L).build();
        when(designations.findById(8L)).thenReturn(Optional.of(existing));
        when(designations.existsByCodeIgnoreCaseAndDesigIdNot("SWE", 8L)).thenReturn(false);
        when(designations.existsByTitleIgnoreCaseAndDepartmentIdAndDesigIdNot("Software Engineer", 100L, 8L)).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> service.updateDesignation(8L, request));
        verify(designations, never()).save(any(Designation.class));
    }
}
