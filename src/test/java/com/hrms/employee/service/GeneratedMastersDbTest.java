package com.hrms.employee.service;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.employee.DatabaseTest;
import com.hrms.employee.dto.request.CityRequest;
import com.hrms.employee.dto.request.CompanyBranchRequest;
import com.hrms.employee.dto.request.CompanyRequest;
import com.hrms.employee.dto.request.DirectorateRequest;
import com.hrms.employee.dto.request.GradeRequest;
import com.hrms.employee.dto.request.LanguageLevelRequest;
import com.hrms.employee.dto.request.LanguageRequest;
import com.hrms.employee.dto.request.ProvinceRequest;
import com.hrms.employee.dto.request.RegionRequest;
import com.hrms.employee.dto.request.RelationRequest;
import com.hrms.employee.dto.request.RelationTypeRequest;
import com.hrms.employee.dto.request.WilayaRequest;
import com.hrms.employee.dto.response.CompanyBranchResponse;
import com.hrms.employee.dto.response.GradeResponse;
import com.hrms.employee.dto.response.RegionResponse;
import com.hrms.employee.dto.response.WilayaResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The generated masters against a real Oracle schema built from db/migration (V5, V6): Hibernate's startup check
 * proves every entity matches its table, and these steps prove the rules that need real constraints: unique codes,
 * the parent chain, deleting a row that others refer to, sort order and Arabic text. Each step ends with flush and
 * clear, because a real request starts with an empty persistence context.
 */
@Transactional
class GeneratedMastersDbTest extends DatabaseTest {

    @Autowired RegionService regions;
    @Autowired WilayaService wilayas;
    @Autowired ProvinceService provinces;
    @Autowired CityService cities;
    @Autowired LanguageLevelService languageLevels;
    @Autowired RelationTypeService relationTypes;
    @Autowired RelationService relations;
    @Autowired LanguageService languages;
    @Autowired GradeService grades;
    @Autowired CompanyService companies;
    @Autowired CompanyBranchService companyBranches;
    @Autowired DirectorateService directorates;
    @PersistenceContext EntityManager em;

    private void fresh() {
        em.flush();
        em.clear();
    }

    private RegionResponse region(String code) {
        RegionResponse r = regions.create(RegionRequest.builder().code(code).name("Region " + code).nameAr("إقليم " + code).build());
        fresh();
        return r;
    }

    private WilayaResponse wilaya(Long regionId, String code) {
        WilayaResponse w = wilayas.create(WilayaRequest.builder()
            .regionId(regionId).code(code).name("Wilaya " + code).nameAr("ولاية " + code).build());
        fresh();
        return w;
    }

    @Test
    void languageLevels_areListedByProficiency_notAlphabetically() {
        languageLevels.create(LanguageLevelRequest.builder().code("T_ZULU").name("Zulu level").nameAr("ز").sortOrder(10).build());
        languageLevels.create(LanguageLevelRequest.builder().code("T_ALPHA").name("Alpha level").nameAr("أ").sortOrder(20).build());
        fresh();
        List<String> codes = languageLevels.getActiveLookup().stream().map(l -> l.getCode()).toList();
        assertTrue(codes.indexOf("T_ZULU") < codes.indexOf("T_ALPHA"));
    }

    @Test
    void geographyChain_keepsItsParents_andProtectsRowsThatHaveChildren() {
        RegionResponse region = region("T_EQUATORIA");
        WilayaResponse wilaya = wilaya(region.getId(), "T_CENTRAL");
        Long provinceId = provinces.create(ProvinceRequest.builder()
            .wilayaId(wilaya.getId()).code("T_JUBA").name("Juba").nameAr("جوبا").build()).getId();
        fresh();
        Long cityId = cities.create(CityRequest.builder()
            .provinceId(provinceId).code("T_JUBA_CITY").name("Juba").nameAr("جوبا").build()).getId();
        fresh();

        assertEquals(provinceId, cities.getById(cityId).getProvinceId());
        assertEquals("جوبا", cities.getById(cityId).getNameAr());
        assertNotNull(cities.getById(cityId).getCreatedAt());   // from the column default, read back

        // the paged search filters by text (name, Arabic name, code), province and status, and counts the total
        assertEquals(1, cities.search("juba_c", provinceId, true, 0, 10).getTotalElements());   // code, "_" is literal
        assertEquals(1, cities.search("جوبا", null, null, 0, 10).getTotalElements());
        assertEquals(0, cities.search("juba", provinceId, false, 0, 10).getTotalElements());
        assertEquals(0, cities.search("juba", provinceId + 999, null, 0, 10).getTotalElements());
        assertEquals(0, cities.search("%", provinceId, null, 0, 10).getTotalElements());        // "%" is not a wildcard

        // a code is unique for the whole table
        assertThrows(DuplicateResourceException.class, () -> wilaya(region.getId(), "T_CENTRAL"));

        // deleting or switching off a row that has rows below it is refused with a rule code, nothing is lost
        assertEquals("MASTER_IN_USE", assertThrows(BusinessRuleException.class, () -> regions.delete(region.getId())).getRuleCode());
        assertEquals("MASTER_HAS_ACTIVE_CHILDREN",
            assertThrows(BusinessRuleException.class, () -> wilayas.deactivate(wilaya.getId())).getRuleCode());

        // a wilaya with provinces cannot move to another region
        RegionResponse other = region("T_BAHR");
        WilayaRequest move = WilayaRequest.builder().regionId(other.getId()).code("T_CENTRAL")
            .name("Wilaya T_CENTRAL").nameAr("x").version(wilaya.getVersion()).build();
        assertEquals("MASTER_HAS_CHILDREN",
            assertThrows(BusinessRuleException.class, () -> wilayas.update(wilaya.getId(), move)).getRuleCode());

        // switching off from the bottom up works, and an inactive parent then blocks an active child
        cities.deactivate(cityId);
        provinces.deactivate(provinceId);
        fresh();
        wilayas.deactivate(wilaya.getId());
        fresh();
        assertEquals("PARENT_INACTIVE",
            assertThrows(BusinessRuleException.class, () -> provinces.activate(provinceId)).getRuleCode());
    }

    @Test
    void relationUnderItsType_andALeafCanBeDeleted() {
        Long typeId = relationTypes.create(RelationTypeRequest.builder()
            .code("T_FAMILY").name("Family").nameAr("أسرة").build()).getId();
        fresh();
        Long relationId = relations.create(RelationRequest.builder()
            .relationTypeId(typeId).code("T_SPOUSE").name("Spouse").nameAr("زوج").build()).getId();
        fresh();

        assertEquals(typeId, relations.getById(relationId).getRelationTypeId());
        assertEquals("MASTER_IN_USE", assertThrows(BusinessRuleException.class, () -> relationTypes.delete(typeId)).getRuleCode());

        relations.delete(relationId);
        fresh();
        relationTypes.delete(typeId);
        fresh();
        assertTrue(relationTypes.getAll().stream().noneMatch(t -> t.getId().equals(typeId)));
    }

    @Test
    void gradesAreListedBySortOrder_notByName() {
        GradeResponse g2 = grades.create(GradeRequest.builder().code("T_G02").name("Zeta").nameAr("ب").sortOrder(902).build());
        GradeResponse g1 = grades.create(GradeRequest.builder().code("T_G01").name("Alpha").nameAr("أ").sortOrder(901).build());
        fresh();

        List<Long> order = grades.getAll().stream().map(GradeResponse::getId)
            .filter(id -> id.equals(g1.getId()) || id.equals(g2.getId())).toList();

        assertEquals(List.of(g1.getId(), g2.getId()), order);
        assertEquals(902, grades.getById(g2.getId()).getSortOrder());
    }

    @Test
    void languageKeepsArabicText() {
        Long id = languages.create(LanguageRequest.builder().code("T_AR").name("Arabic").nameAr("العربية").build()).getId();
        fresh();

        assertEquals("العربية", languages.getById(id).getNameAr());
    }

    @Test
    void directorateBelongsToACompanyBranch_andItsBranchCannotBeSwitchedOffWhileItIsActive() {
        Long companyId = companies.create(CompanyRequest.builder().code("T_CO").name("Co").nameAr("ش").build()).getId();
        fresh();
        CompanyBranchResponse branch = companyBranches.create(CompanyBranchRequest.builder()
            .companyId(companyId).code("T_BR").name("Branch").nameAr("ف").build());
        fresh();
        Long directorateId = directorates.create(DirectorateRequest.builder()
            .companyBranchId(branch.getId()).code("T_DIR").name("Finance").nameAr("المالية").build()).getId();
        fresh();

        assertEquals(branch.getId(), directorates.getById(directorateId).getCompanyBranchId());
        assertEquals("MASTER_HAS_ACTIVE_CHILDREN",
            assertThrows(BusinessRuleException.class, () -> companyBranches.deactivate(branch.getId())).getRuleCode());

        directorates.deactivate(directorateId);
        fresh();
        companyBranches.deactivate(branch.getId());   // now allowed
    }
}
