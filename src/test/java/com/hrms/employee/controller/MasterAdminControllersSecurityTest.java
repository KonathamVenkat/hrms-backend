package com.hrms.employee.controller;

import com.hrms.common.exception.GlobalExceptionHandler;
import com.hrms.employee.service.BankBranchService;
import com.hrms.employee.service.BankService;
import com.hrms.employee.service.CityService;
import com.hrms.employee.service.CompanyBranchService;
import com.hrms.employee.service.CompanyService;
import com.hrms.employee.service.ContractTypeService;
import com.hrms.employee.service.DirectorateService;
import com.hrms.employee.service.EducationLevelService;
import com.hrms.employee.service.GradeService;
import com.hrms.employee.service.HouseTypeService;
import com.hrms.employee.service.LanguageLevelService;
import com.hrms.employee.service.LanguageService;
import com.hrms.employee.service.NationalityService;
import com.hrms.employee.service.ProfessionService;
import com.hrms.employee.service.ProvinceService;
import com.hrms.employee.service.RegionService;
import com.hrms.employee.service.RelationService;
import com.hrms.employee.service.RelationTypeService;
import com.hrms.employee.service.ReligionService;
import com.hrms.employee.service.WilayaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every master maintenance controller (banks, companies, geography, employee reference lists, directorates, grades)
 * is SYSTEM_ADMIN only: every verb on every controller must refuse HR_ADMIN and EMPLOYEE and let SYSTEM_ADMIN
 * through. The JWT filter chain is not part of this test (same setup as EmployeeEndpointSecurityTest).
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = MasterAdminControllersSecurityTest.TestConfig.class)
class MasterAdminControllersSecurityTest {

    // Not annotated @Configuration on purpose, see EmployeeEndpointSecurityTest.
    @EnableWebMvc
    @EnableMethodSecurity
    static class TestConfig {
        @Bean GlobalExceptionHandler globalExceptionHandler() { return new GlobalExceptionHandler(); }
        @Bean BankAdminController bank() { return new BankAdminController(mock(BankService.class)); }
        @Bean BankBranchAdminController bankBranch() { return new BankBranchAdminController(mock(BankBranchService.class)); }
        @Bean CompanyAdminController company() { return new CompanyAdminController(mock(CompanyService.class)); }
        @Bean CompanyBranchAdminController companyBranch() { return new CompanyBranchAdminController(mock(CompanyBranchService.class)); }
        @Bean RegionAdminController region() { return new RegionAdminController(mock(RegionService.class)); }
        @Bean WilayaAdminController wilaya() { return new WilayaAdminController(mock(WilayaService.class)); }
        @Bean ProvinceAdminController province() { return new ProvinceAdminController(mock(ProvinceService.class)); }
        @Bean CityAdminController city() { return new CityAdminController(mock(CityService.class)); }
        @Bean ReligionAdminController religion() { return new ReligionAdminController(mock(ReligionService.class)); }
        @Bean NationalityAdminController nationality() { return new NationalityAdminController(mock(NationalityService.class)); }
        @Bean ContractTypeAdminController contractType() { return new ContractTypeAdminController(mock(ContractTypeService.class)); }
        @Bean ProfessionAdminController profession() { return new ProfessionAdminController(mock(ProfessionService.class)); }
        @Bean EducationLevelAdminController educationLevel() { return new EducationLevelAdminController(mock(EducationLevelService.class)); }
        @Bean LanguageAdminController language() { return new LanguageAdminController(mock(LanguageService.class)); }
        @Bean LanguageLevelAdminController languageLevel() { return new LanguageLevelAdminController(mock(LanguageLevelService.class)); }
        @Bean HouseTypeAdminController houseType() { return new HouseTypeAdminController(mock(HouseTypeService.class)); }
        @Bean RelationTypeAdminController relationType() { return new RelationTypeAdminController(mock(RelationTypeService.class)); }
        @Bean RelationAdminController relation() { return new RelationAdminController(mock(RelationService.class)); }
        @Bean DirectorateAdminController directorate() { return new DirectorateAdminController(mock(DirectorateService.class)); }
        @Bean GradeAdminController grade() { return new GradeAdminController(mock(GradeService.class)); }
    }

    static Stream<String> paths() {
        return Stream.of("banks", "bank-branches", "companies", "company-branches",
            "regions", "wilayas", "provinces", "cities",
            "religions", "nationalities", "contract-types", "professions", "education-levels",
            "languages", "language-levels", "house-types", "relation-types", "relations",
            "directorates", "grades");
    }

    /** One body that satisfies every master's validation (each ignores the parent ids it does not have). */
    private static final String BODY = "{\"code\":\"A1\",\"name\":\"n\",\"nameAr\":\"n\",\"version\":0,\"sortOrder\":1,"
        + "\"bankId\":1,\"companyId\":1,\"regionId\":1,\"wilayaId\":1,\"provinceId\":1,\"relationTypeId\":1,\"companyBranchId\":1}";

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    /** Calls every endpoint; a refused caller gets 403 on all of them, an allowed one gets 200 (201 on create). */
    private void everyVerb(String path, int expected) throws Exception {
        String base = "/api/v1/admin/" + path;
        int created = expected == 200 ? 201 : expected;
        mvc.perform(get(base)).andExpect(status().is(expected));
        mvc.perform(get(base + "/1")).andExpect(status().is(expected));
        mvc.perform(post(base).contentType("application/json").content(BODY)).andExpect(status().is(created));
        mvc.perform(put(base + "/1").contentType("application/json").content(BODY)).andExpect(status().is(expected));
        mvc.perform(patch(base + "/1/deactivate")).andExpect(status().is(expected));
        mvc.perform(patch(base + "/1/activate")).andExpect(status().is(expected));
        mvc.perform(delete(base + "/1")).andExpect(status().is(expected));
    }

    @ParameterizedTest
    @MethodSource("paths")
    @WithMockUser(roles = "HR_ADMIN")
    void hrAdminIsForbidden(String path) throws Exception {
        everyVerb(path, 403);
    }

    @ParameterizedTest
    @MethodSource("paths")
    @WithMockUser(roles = "HR_MANAGER")
    void hrManagerIsForbidden(String path) throws Exception {
        everyVerb(path, 403);
    }

    @ParameterizedTest
    @MethodSource("paths")
    @WithMockUser(roles = "EMPLOYEE")
    void employeeIsForbidden(String path) throws Exception {
        everyVerb(path, 403);
    }

    @ParameterizedTest
    @MethodSource("paths")
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void systemAdminIsAllowed(String path) throws Exception {
        everyVerb(path, 200);
    }
}
