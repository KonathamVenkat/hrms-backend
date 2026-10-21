package com.hrms.employee.service;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.EmployeeDependentRequest;
import com.hrms.employee.dto.request.EmployeeEducationRequest;
import com.hrms.employee.dto.request.EmployeeLanguageRequest;
import com.hrms.employee.dto.response.EmployeeDependentResponse;
import com.hrms.employee.dto.response.EmployeeEducationResponse;
import com.hrms.employee.entity.EducationLevel;
import com.hrms.employee.entity.EmployeeDependent;
import com.hrms.employee.entity.EmployeeEducation;
import com.hrms.employee.entity.EmployeeLanguage;
import com.hrms.employee.entity.Language;
import com.hrms.employee.entity.LanguageLevel;
import com.hrms.employee.entity.Relation;
import com.hrms.employee.repository.EducationLevelRepository;
import com.hrms.employee.repository.EmployeeDependentRepository;
import com.hrms.employee.repository.EmployeeEducationRepository;
import com.hrms.employee.repository.EmployeeLanguageRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.repository.LanguageLevelRepository;
import com.hrms.employee.repository.LanguageRepository;
import com.hrms.employee.repository.RelationRepository;
import com.hrms.employee.service.impl.EmployeeDependentServiceImpl;
import com.hrms.employee.service.impl.EmployeeEducationServiceImpl;
import com.hrms.employee.service.impl.EmployeeLanguageServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The six employee-information services are generated from one template (tools/generate_employee_info.py).
 * Dependants (a required master value, names, defaults), education (dates, an optional master value) and languages
 * (a duplicate rule, three optional master values) cover every variant of it.
 */
@ExtendWith(MockitoExtension.class)
class EmployeeInfoServicesTest {

    private static final long EMP = 10L;

    @Mock EmployeeRepository employeeRepository;
    @Mock EmployeeDependentRepository dependentRepository;
    @Mock EmployeeEducationRepository educationRepository;
    @Mock EmployeeLanguageRepository languageRecordRepository;
    @Mock RelationRepository relationRepository;
    @Mock EducationLevelRepository educationLevelRepository;
    @Mock LanguageRepository languageRepository;
    @Mock LanguageLevelRepository languageLevelRepository;
    @Mock AuditTrail audit;
    @Mock EntityManager entityManager;

    EmployeeDependentServiceImpl dependants;
    EmployeeEducationServiceImpl education;
    EmployeeLanguageServiceImpl languages;

    @BeforeEach
    void setUp() {
        dependants = new EmployeeDependentServiceImpl(dependentRepository, employeeRepository, relationRepository, audit, entityManager);
        education = new EmployeeEducationServiceImpl(educationRepository, employeeRepository, educationLevelRepository, audit, entityManager);
        languages = new EmployeeLanguageServiceImpl(languageRecordRepository, employeeRepository, languageRepository,
            languageLevelRepository, audit, entityManager);
    }

    private void employeeExists() {
        when(employeeRepository.existsById(EMP)).thenReturn(true);
    }

    private static Relation relation(long id, boolean active) {
        return Relation.builder().id(id).relationTypeId(1L).code("R" + id).name("Spouse").nameAr("زوج")
            .isActive(active).version(0L).build();
    }

    private static EmployeeDependent dependant(long id, long employeeId, long relationId, boolean active) {
        return EmployeeDependent.builder().id(id).employeeId(employeeId).relationId(relationId).firstName("Nyadak")
            .isAlive(true).isActive(active).version(3L).build();
    }

    private static EmployeeDependentRequest dependantRequest(long relationId) {
        return EmployeeDependentRequest.builder().relationId(relationId).firstName("  Nyadak ").lastName(" Lado ")
            .phone("  ").version(3L).build();
    }

    // ── dependants ────────────────────────────────────────────

    @Test
    void dependant_add_trimsValues_defaultsToAlive_resolvesTheRelationName_andAuditsIdsOnly() {
        employeeExists();
        when(relationRepository.findById(1L)).thenReturn(Optional.of(relation(1, true)));
        when(relationRepository.findAllById(any())).thenReturn(List.of(relation(1, true)));
        when(dependentRepository.save(any(EmployeeDependent.class))).thenAnswer(i -> i.getArgument(0));

        EmployeeDependentResponse out = dependants.add(EMP, dependantRequest(1));

        assertEquals("Nyadak", out.getFirstName());
        assertEquals("Lado", out.getLastName());
        assertNull(out.getPhone());              // a blank optional value is stored as null
        assertTrue(out.getIsAlive());
        assertTrue(out.getIsActive());
        assertEquals("Spouse", out.getRelationName());
        assertEquals("زوج", out.getRelationNameAr());
        verify(audit).record(eq("EMPLOYEE_DEPENDENT_ADDED"), eq("EMPLOYEE_DEPENDENT"), any(), eq("employeeId=10, active=true"));
    }

    @Test
    void dependant_add_refusesAnInactiveOrUnknownRelation() {
        employeeExists();
        when(relationRepository.findById(1L)).thenReturn(Optional.of(relation(1, false)));
        when(relationRepository.findById(2L)).thenReturn(Optional.empty());

        assertEquals("RELATION_INACTIVE",
            assertThrows(BusinessRuleException.class, () -> dependants.add(EMP, dependantRequest(1))).getRuleCode());
        assertThrows(ResourceNotFoundException.class, () -> dependants.add(EMP, dependantRequest(2)));
        verify(dependentRepository, never()).save(any());
    }

    @Test
    void dependant_add_forAnUnknownEmployeeIsNotFound() {
        when(employeeRepository.existsById(EMP)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> dependants.add(EMP, dependantRequest(1)));
        verify(dependentRepository, never()).save(any());
    }

    @Test
    void dependant_update_mayKeepAnInactiveRelationItAlreadyHolds_butCannotSwitchToOne() {
        when(dependentRepository.findById(5L)).thenReturn(Optional.of(dependant(5, EMP, 1, true)));
        when(relationRepository.findById(1L)).thenReturn(Optional.of(relation(1, false)));   // now inactive
        when(relationRepository.findById(2L)).thenReturn(Optional.of(relation(2, false)));
        when(dependentRepository.save(any(EmployeeDependent.class))).thenAnswer(i -> i.getArgument(0));

        assertDoesNotThrow(() -> dependants.update(EMP, 5L, dependantRequest(1)));
        assertEquals("RELATION_INACTIVE",
            assertThrows(BusinessRuleException.class, () -> dependants.update(EMP, 5L, dependantRequest(2))).getRuleCode());
    }

    @Test
    void update_needsTheCurrentVersion_andAnActiveRecord() {
        when(dependentRepository.findById(5L)).thenReturn(Optional.of(dependant(5, EMP, 1, true)));
        EmployeeDependentRequest noVersion = dependantRequest(1);
        noVersion.setVersion(null);
        EmployeeDependentRequest stale = dependantRequest(1);
        stale.setVersion(1L);

        assertThrows(BadRequestException.class, () -> dependants.update(EMP, 5L, noVersion));                 // 400
        assertThrows(OptimisticLockingFailureException.class, () -> dependants.update(EMP, 5L, stale));       // 409

        when(dependentRepository.findById(6L)).thenReturn(Optional.of(dependant(6, EMP, 1, false)));
        assertEquals("RECORD_INACTIVE",
            assertThrows(BusinessRuleException.class, () -> dependants.update(EMP, 6L, dependantRequest(1))).getRuleCode());
        verify(dependentRepository, never()).save(any());
    }

    @Test
    void aRecordOfAnotherEmployeeIsNotFound_forEveryWriteAndNeverChanged() {
        when(dependentRepository.findById(5L)).thenReturn(Optional.of(dependant(5, 99L, 1, true)));

        assertThrows(ResourceNotFoundException.class, () -> dependants.update(EMP, 5L, dependantRequest(1)));
        assertThrows(ResourceNotFoundException.class, () -> dependants.deactivate(EMP, 5L));
        assertThrows(ResourceNotFoundException.class, () -> dependants.activate(EMP, 5L));
        verify(dependentRepository, never()).save(any());
    }

    @Test
    void deactivateAndActivate_changeTheFlag_andAreSafeToRepeat() {
        EmployeeDependent active = dependant(5, EMP, 1, true);
        EmployeeDependent inactive = dependant(6, EMP, 1, false);
        when(dependentRepository.findById(5L)).thenReturn(Optional.of(active));
        when(dependentRepository.findById(6L)).thenReturn(Optional.of(inactive));

        dependants.deactivate(EMP, 5L);
        dependants.deactivate(EMP, 5L);   // already inactive now: nothing happens
        dependants.activate(EMP, 6L);
        dependants.activate(EMP, 6L);     // already active now: nothing happens

        assertFalse(active.getIsActive());
        assertTrue(inactive.getIsActive());
        verify(dependentRepository, times(2)).save(any());   // one real change each, never a second effect
        verify(audit).record(eq("EMPLOYEE_DEPENDENT_DEACTIVATED"), anyString(), eq(5L), anyString());
        verify(audit).record(eq("EMPLOYEE_DEPENDENT_ACTIVATED"), anyString(), eq(6L), anyString());
    }

    // ── education ─────────────────────────────────────────────

    private static EmployeeEducationRequest educationRequest(LocalDate start, LocalDate end, Long levelId) {
        return EmployeeEducationRequest.builder().qualification("BSc Accounting").institution("University of Juba")
            .educationLevelId(levelId).startDate(start).endDate(end).build();
    }

    @Test
    void education_endBeforeStartIsRefused_butTheSameDayOrAnOpenEndIsFine() {
        employeeExists();
        when(educationRepository.save(any(EmployeeEducation.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals("INVALID_DATE_RANGE", assertThrows(BusinessRuleException.class, () -> education.add(EMP,
            educationRequest(LocalDate.of(2012, 6, 1), LocalDate.of(2008, 9, 1), null))).getRuleCode());
        assertDoesNotThrow(() -> education.add(EMP, educationRequest(LocalDate.of(2012, 6, 1), LocalDate.of(2012, 6, 1), null)));
        assertDoesNotThrow(() -> education.add(EMP, educationRequest(LocalDate.of(2012, 6, 1), null, null)));
    }

    @Test
    void education_theOptionalLevelMustBeActiveWhenGiven_andIsNeverLookedUpWhenLeftOut() {
        employeeExists();
        when(educationLevelRepository.findById(4L)).thenReturn(Optional.of(
            EducationLevel.builder().id(4L).code("BACHELOR").name("Bachelor").nameAr("x").isActive(false).version(0L).build()));

        assertEquals("EDUCATION_LEVEL_INACTIVE", assertThrows(BusinessRuleException.class,
            () -> education.add(EMP, educationRequest(null, null, 4L))).getRuleCode());

        when(educationRepository.save(any(EmployeeEducation.class))).thenAnswer(i -> i.getArgument(0));
        EmployeeEducationResponse out = education.add(EMP, educationRequest(null, null, null));
        assertNull(out.getEducationLevelId());
        verify(educationLevelRepository, times(1)).findById(any());   // only the first call asked
    }

    @Test
    void education_isListedActiveFirst_thenNewestStartFirst_andRecordsWithoutDatesLast() {
        employeeExists();
        when(educationRepository.findByEmployeeIdOrderByIdAsc(EMP)).thenReturn(List.of(
            EmployeeEducation.builder().id(1L).employeeId(EMP).qualification("Old").startDate(LocalDate.of(2005, 1, 1)).isActive(true).version(0L).build(),
            EmployeeEducation.builder().id(2L).employeeId(EMP).qualification("Undated").isActive(true).version(0L).build(),
            EmployeeEducation.builder().id(3L).employeeId(EMP).qualification("Switched off").startDate(LocalDate.of(2020, 1, 1)).isActive(false).version(0L).build(),
            EmployeeEducation.builder().id(4L).employeeId(EMP).qualification("Recent").startDate(LocalDate.of(2015, 1, 1)).isActive(true).version(0L).build()));

        List<String> order = education.getAll(EMP).stream().map(EmployeeEducationResponse::getQualification).toList();

        assertEquals(List.of("Recent", "Old", "Undated", "Switched off"), order);
    }

    // ── languages ─────────────────────────────────────────────

    private static EmployeeLanguageRequest languageRequest(long languageId, Long read, Long write, Long speak) {
        return EmployeeLanguageRequest.builder().languageId(languageId).readLevelId(read).writeLevelId(write)
            .speakLevelId(speak).version(0L).build();
    }

    private static Language language(long id) {
        return Language.builder().id(id).code("L" + id).name("English").nameAr("x").isActive(true).version(0L).build();
    }

    private static LanguageLevel level(long id, boolean active) {
        return LanguageLevel.builder().id(id).code("LV" + id).name("Fluent").nameAr("x").isActive(active).version(0L).build();
    }

    @Test
    void language_addTwiceIsRefused_andSoIsSwitchingToOneAlreadyRecorded() {
        employeeExists();
        when(languageRepository.findById(1L)).thenReturn(Optional.of(language(1)));
        when(languageRepository.findById(2L)).thenReturn(Optional.of(language(2)));
        when(languageRecordRepository.existsByEmployeeIdAndLanguageId(EMP, 1L)).thenReturn(true);

        assertEquals("DUPLICATE_LANGUAGE", assertThrows(BusinessRuleException.class,
            () -> languages.add(EMP, languageRequest(1, null, null, null))).getRuleCode());

        EmployeeLanguage existing = EmployeeLanguage.builder().id(7L).employeeId(EMP).languageId(1L).isActive(true).version(0L).build();
        when(languageRecordRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(languageRecordRepository.existsByEmployeeIdAndLanguageIdAndIdNot(EMP, 2L, 7L)).thenReturn(true);
        assertEquals("DUPLICATE_LANGUAGE", assertThrows(BusinessRuleException.class,
            () -> languages.update(EMP, 7L, languageRequest(2, null, null, null))).getRuleCode());
        verify(languageRecordRepository, never()).save(any());
    }

    @Test
    void language_levelsAreOptional_butAnInactiveOneIsRefused_andOnlyTheGivenOnesAreLookedUp() {
        employeeExists();
        when(languageRepository.findById(1L)).thenReturn(Optional.of(language(1)));
        when(languageLevelRepository.findById(3L)).thenReturn(Optional.of(level(3, true)));
        when(languageLevelRepository.findById(4L)).thenReturn(Optional.of(level(4, false)));
        when(languageRecordRepository.save(any(EmployeeLanguage.class))).thenAnswer(i -> i.getArgument(0));

        assertDoesNotThrow(() -> languages.add(EMP, languageRequest(1, 3L, null, null)));
        assertEquals("LANGUAGE_LEVEL_INACTIVE", assertThrows(BusinessRuleException.class,
            () -> languages.add(EMP, languageRequest(1, 3L, 4L, null))).getRuleCode());
        verify(languageLevelRepository, never()).findById(isNull());
    }
}
