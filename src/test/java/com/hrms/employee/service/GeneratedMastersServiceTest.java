package com.hrms.employee.service;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.employee.dto.request.DirectorateRequest;
import com.hrms.employee.dto.request.GradeRequest;
import com.hrms.employee.dto.request.WilayaRequest;
import com.hrms.employee.dto.response.GradeResponse;
import com.hrms.employee.entity.CompanyBranch;
import com.hrms.employee.entity.Grade;
import com.hrms.employee.entity.Region;
import com.hrms.employee.entity.Wilaya;
import com.hrms.employee.repository.CompanyBranchRepository;
import com.hrms.employee.repository.CompanyRepository;
import com.hrms.employee.repository.DirectorateRepository;
import com.hrms.employee.repository.GradeRepository;
import com.hrms.employee.repository.ProvinceRepository;
import com.hrms.employee.repository.RegionRepository;
import com.hrms.employee.repository.WilayaRepository;
import com.hrms.employee.service.impl.CompanyBranchServiceImpl;
import com.hrms.employee.service.impl.DirectorateServiceImpl;
import com.hrms.employee.service.impl.GradeServiceImpl;
import com.hrms.employee.service.impl.RegionServiceImpl;
import com.hrms.employee.service.impl.WilayaServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The generated masters (tools/generate_masters.py) share one shape. Region (parent of wilayas), Wilaya (child and
 * parent), Grade (sort order, no parent), Directorate (parent is a company branch) and the hand-added directorate rule
 * on company branches cover every variant of it.
 */
@ExtendWith(MockitoExtension.class)
class GeneratedMastersServiceTest {

    @Mock RegionRepository regionRepository;
    @Mock WilayaRepository wilayaRepository;
    @Mock ProvinceRepository provinceRepository;
    @Mock GradeRepository gradeRepository;
    @Mock DirectorateRepository directorateRepository;
    @Mock CompanyBranchRepository companyBranchRepository;
    @Mock CompanyRepository companyRepository;
    @Mock AuditTrail audit;
    @Mock EntityManager entityManager;

    private Region region(long id, boolean active) {
        return Region.builder().id(id).code("R" + id).name("Region").nameAr("x").isActive(active).version(0L).build();
    }

    private Wilaya wilaya(long id, long regionId, boolean active) {
        return Wilaya.builder().id(id).regionId(regionId).code("W" + id).name("Wilaya").nameAr("x")
            .isActive(active).version(0L).build();
    }

    private WilayaServiceImpl wilayaService() {
        return new WilayaServiceImpl(wilayaRepository, regionRepository, provinceRepository, audit, entityManager);
    }

    private WilayaRequest wilayaRequest(long regionId) {
        return WilayaRequest.builder().regionId(regionId).code(" w1 ").name(" Central ").nameAr(" x ").version(0L).build();
    }

    @Test
    void region_cannotBeDeactivatedOrDeletedWhileItHasWilayas() {
        RegionServiceImpl service = new RegionServiceImpl(regionRepository, wilayaRepository, audit, entityManager);
        when(regionRepository.findById(1L)).thenReturn(Optional.of(region(1, true)));
        when(wilayaRepository.existsByRegionIdAndIsActive(1L, true)).thenReturn(true);
        when(wilayaRepository.existsByRegionId(1L)).thenReturn(true);

        assertEquals("MASTER_HAS_ACTIVE_CHILDREN", assertThrows(BusinessRuleException.class, () -> service.deactivate(1L)).getRuleCode());
        assertEquals("MASTER_IN_USE", assertThrows(BusinessRuleException.class, () -> service.delete(1L)).getRuleCode());
        verify(regionRepository, never()).delete(any());
    }

    @Test
    void wilaya_createNormalisesCodeAndNeedsAnActiveRegion() {
        when(wilayaRepository.existsByCodeIgnoreCase("W1")).thenReturn(false);
        when(regionRepository.findById(1L)).thenReturn(Optional.of(region(1, true)));
        when(wilayaRepository.save(any(Wilaya.class))).thenAnswer(i -> i.getArgument(0));
        assertEquals("W1", wilayaService().create(wilayaRequest(1)).getCode());

        when(regionRepository.findById(2L)).thenReturn(Optional.of(region(2, false)));
        assertEquals("PARENT_INACTIVE",
            assertThrows(BusinessRuleException.class, () -> wilayaService().create(wilayaRequest(2))).getRuleCode());
        verify(wilayaRepository, times(1)).save(any());
    }

    @Test
    void wilaya_withProvincesCannotMoveToAnotherRegion_orBeSwitchedOff() {
        when(wilayaRepository.findById(5L)).thenReturn(Optional.of(wilaya(5, 1, true)));
        when(wilayaRepository.existsByCodeIgnoreCaseAndIdNot("W1", 5L)).thenReturn(false);
        when(regionRepository.findById(2L)).thenReturn(Optional.of(region(2, true)));
        when(provinceRepository.existsByWilayaId(5L)).thenReturn(true);

        assertEquals("MASTER_HAS_CHILDREN",
            assertThrows(BusinessRuleException.class, () -> wilayaService().update(5L, wilayaRequest(2))).getRuleCode());

        when(provinceRepository.existsByWilayaIdAndIsActive(5L, true)).thenReturn(true);
        assertEquals("MASTER_HAS_ACTIVE_CHILDREN",
            assertThrows(BusinessRuleException.class, () -> wilayaService().deactivate(5L)).getRuleCode());
        verify(wilayaRepository, never()).save(any());
    }

    @Test
    void wilaya_updateNeedsTheCurrentVersion() {
        when(wilayaRepository.findById(5L)).thenReturn(Optional.of(wilaya(5, 1, true)));
        WilayaRequest noVersion = wilayaRequest(1);
        noVersion.setVersion(null);

        assertThrows(BadRequestException.class, () -> wilayaService().update(5L, noVersion));
    }

    private GradeServiceImpl gradeService() {
        return new GradeServiceImpl(gradeRepository, audit, entityManager);
    }

    @Test
    void grade_keepsItsSortOrder_andIsListedBySortOrder() {
        when(gradeRepository.existsByCodeIgnoreCase("G25")).thenReturn(false);
        when(gradeRepository.save(any(Grade.class))).thenAnswer(i -> i.getArgument(0));

        GradeResponse out = gradeService().create(GradeRequest.builder()
            .code(" g25 ").name("Grade 25").nameAr("x").sortOrder(25).build());

        assertEquals("G25", out.getCode());
        assertEquals(25, out.getSortOrder());

        when(gradeRepository.findAllByOrderBySortOrderAscNameAsc()).thenReturn(List.of(
            Grade.builder().id(1L).code("G01").name("Grade 1").nameAr("x").sortOrder(1).isActive(true).version(0L).build()));
        assertEquals(1, gradeService().getAll().get(0).getSortOrder());
        verify(gradeRepository).findAllByOrderBySortOrderAscNameAsc();
    }

    @Test
    void grade_duplicateCodeIsRejected_andADeleteBlockedByAForeignKeyReportsInUse() {
        when(gradeRepository.existsByCodeIgnoreCase("G01")).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () -> gradeService().create(GradeRequest.builder()
            .code("G01").name("Grade 1").nameAr("x").build()));

        when(gradeRepository.findById(3L)).thenReturn(Optional.of(
            Grade.builder().id(3L).code("G03").name("Grade 3").nameAr("x").isActive(true).version(0L).build()));
        doThrow(new DataIntegrityViolationException("fk")).when(gradeRepository).flush();
        assertEquals("MASTER_IN_USE",
            assertThrows(BusinessRuleException.class, () -> gradeService().delete(3L)).getRuleCode());
    }

    private CompanyBranch branch(boolean active) {
        return CompanyBranch.builder().id(9L).companyId(1L).code("B9").name("Branch").nameAr("x")
            .isActive(active).version(0L).build();
    }

    @Test
    void directorate_createNeedsAnActiveCompanyBranch() {
        DirectorateServiceImpl service =
            new DirectorateServiceImpl(directorateRepository, companyBranchRepository, audit, entityManager);
        DirectorateRequest request = DirectorateRequest.builder()
            .companyBranchId(9L).code("D1").name("Finance").nameAr("x").build();
        when(directorateRepository.existsByCodeIgnoreCase("D1")).thenReturn(false);
        when(companyBranchRepository.findById(9L)).thenReturn(Optional.of(branch(false)));

        assertEquals("PARENT_INACTIVE", assertThrows(BusinessRuleException.class, () -> service.create(request)).getRuleCode());
        verify(directorateRepository, never()).save(any());
    }

    @Test
    void companyBranch_cannotBeDeactivatedWhileItHasActiveDirectorates() {
        CompanyBranchServiceImpl service = new CompanyBranchServiceImpl(
            companyBranchRepository, companyRepository, directorateRepository, audit, entityManager);
        when(companyBranchRepository.findById(9L)).thenReturn(Optional.of(branch(true)));
        when(directorateRepository.existsByCompanyBranchIdAndIsActive(9L, true)).thenReturn(true);

        assertEquals("MASTER_HAS_ACTIVE_CHILDREN",
            assertThrows(BusinessRuleException.class, () -> service.deactivate(9L)).getRuleCode());
        verify(companyBranchRepository, never()).save(any());
    }
}
