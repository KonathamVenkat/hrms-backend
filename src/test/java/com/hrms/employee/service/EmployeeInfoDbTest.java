package com.hrms.employee.service;

import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.DatabaseTest;
import com.hrms.employee.dto.request.EducationLevelRequest;
import com.hrms.employee.dto.request.EmployeeDependentRequest;
import com.hrms.employee.dto.request.EmployeeEducationRequest;
import com.hrms.employee.dto.request.EmployeeExperienceRequest;
import com.hrms.employee.dto.request.EmployeeLanguageRequest;
import com.hrms.employee.dto.request.EmployeeReferenceRequest;
import com.hrms.employee.dto.request.EmployeeTrainingRequest;
import com.hrms.employee.dto.request.LanguageLevelRequest;
import com.hrms.employee.dto.request.LanguageRequest;
import com.hrms.employee.dto.request.RelationRequest;
import com.hrms.employee.dto.request.RelationTypeRequest;
import com.hrms.employee.dto.response.EmployeeDependentResponse;
import com.hrms.employee.dto.response.EmployeeEducationResponse;
import com.hrms.employee.dto.response.EmployeeLanguageResponse;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.EmployeeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The six employee-information services against a real Oracle schema built from db/migration (V5, V6): column
 * names and types (Hibernate's startup check plus a real insert and read-back of each), the language unique
 * constraint, Arabic text, names resolved from the masters, ordering, and one employee never seeing another's records.
 * Each step ends with flush and clear, because a real request starts with an empty persistence context.
 */
@Transactional
class EmployeeInfoDbTest extends DatabaseTest {

    @Autowired EmployeeDependentService dependants;
    @Autowired EmployeeEducationService education;
    @Autowired EmployeeTrainingService training;
    @Autowired EmployeeLanguageService languages;
    @Autowired EmployeeExperienceService experience;
    @Autowired EmployeeReferenceService references;
    @Autowired RelationTypeService relationTypes;
    @Autowired RelationService relations;
    @Autowired LanguageService languageMasters;
    @Autowired LanguageLevelService levelMasters;
    @Autowired EducationLevelService educationLevels;
    @Autowired EmployeeRepository employeeRepository;
    @PersistenceContext EntityManager em;

    private void fresh() {
        em.flush();
        em.clear();
    }

    private Long employee(String tag) {
        Employee e = employeeRepository.saveAndFlush(Employee.builder()
            .firstName("Amira").firstNameAr("ع").lastName("Nasser" + tag).lastNameAr("ل")
            .dateOfBirth(LocalDate.of(1990, 5, 1)).gender(Gender.FEMALE)
            .personalEmail("info-test-p-" + tag + "@example.test").workEmail("info-test-w-" + tag + "@example.test")
            .hireDate(LocalDate.of(2024, 1, 15)).employmentType(EmploymentType.FULL_TIME).build());
        fresh();
        return e.getId();
    }

    @Test
    void dependant_isStoredWithArabicAndTheMasterNameIsResolved() {
        Long emp = employee("a");
        Long typeId = relationTypes.create(RelationTypeRequest.builder().code("IT_FAM").name("Family").nameAr("أسرة").build()).getId();
        fresh();
        Long relationId = relations.create(RelationRequest.builder()
            .relationTypeId(typeId).code("IT_SPOUSE").name("Spouse").nameAr("زوج").build()).getId();
        fresh();

        EmployeeDependentResponse added = dependants.add(emp, EmployeeDependentRequest.builder()
            .relationId(relationId).firstName("Nyadak").lastName("Lado").firstNameAr("نياداك").gender(Gender.FEMALE)
            .dateOfBirth(LocalDate.of(2016, 3, 12)).phone("+211 912 000 000").build());
        fresh();

        EmployeeDependentResponse read = dependants.getAll(emp).get(0);
        assertEquals(added.getId(), read.getId());
        assertEquals("نياداك", read.getFirstNameAr());
        assertEquals(Gender.FEMALE, read.getGender());
        assertEquals(LocalDate.of(2016, 3, 12), read.getDateOfBirth());
        assertTrue(read.getIsAlive());                 // IS_ALIVE defaults to 1
        assertEquals("Spouse", read.getRelationName());
        assertEquals("زوج", read.getRelationNameAr());
        assertEquals(0L, read.getVersion());
    }

    @Test
    void education_trainingExperienceAndReferenceRoundTrip_withDatesInOrder() {
        Long emp = employee("b");
        Long levelId = educationLevels.create(EducationLevelRequest.builder().code("IT_BSC").name("Bachelor").nameAr("بكالوريوس").build()).getId();
        fresh();

        education.add(emp, EmployeeEducationRequest.builder().educationLevelId(levelId).qualification("BSc Accounting")
            .qualificationAr("بكالوريوس محاسبة").institution("University of Juba")
            .startDate(LocalDate.of(2008, 9, 1)).endDate(LocalDate.of(2012, 6, 30)).build());
        training.add(emp, EmployeeTrainingRequest.builder().courseName("HSE in Fuel Depot Operations")
            .provider("Petroleum Training Centre").startDate(LocalDate.of(2025, 3, 3)).endDate(LocalDate.of(2025, 3, 7)).build());
        experience.add(emp, EmployeeExperienceRequest.builder().employerName("Ministry of Petroleum").jobTitle("Accountant")
            .place("Juba").startDate(LocalDate.of(2012, 7, 1)).build());
        references.add(emp, EmployeeReferenceRequest.builder().firstName("John").lastName("Deng").jobTitle("Director")
            .email("john.deng@example.test").phone("+211 912 111 111").build());
        fresh();

        EmployeeEducationResponse edu = education.getAll(emp).get(0);
        assertEquals("Bachelor", edu.getEducationLevelName());
        assertEquals("بكالوريوس محاسبة", edu.getQualificationAr());
        assertEquals(LocalDate.of(2012, 6, 30), edu.getEndDate());
        assertEquals(1, training.getAll(emp).size());
        assertEquals("Juba", experience.getAll(emp).get(0).getPlace());
        assertNull(experience.getAll(emp).get(0).getEndDate());         // still employed there
        assertEquals("john.deng@example.test", references.getAll(emp).get(0).getEmail());

        // a bad date range is refused by the service before the database constraint would be hit
        assertThrows(BusinessRuleException.class, () -> education.add(emp, EmployeeEducationRequest.builder()
            .qualification("X").startDate(LocalDate.of(2020, 1, 1)).endDate(LocalDate.of(2019, 1, 1)).build()));
    }

    @Test
    void language_isUniquePerEmployee_keepsItsThreeLevels_andCanBeSwitchedOffAndOn() {
        Long emp = employee("c");
        Long other = employee("d");
        Long languageId = languageMasters.create(LanguageRequest.builder().code("IT_AR").name("Arabic").nameAr("العربية").build()).getId();
        Long fluent = levelMasters.create(LanguageLevelRequest.builder().code("IT_FL").name("Fluent").nameAr("طليق").build()).getId();
        Long basic = levelMasters.create(LanguageLevelRequest.builder().code("IT_BA").name("Basic").nameAr("أساسي").build()).getId();
        fresh();

        EmployeeLanguageResponse added = languages.add(emp, EmployeeLanguageRequest.builder().languageId(languageId)
            .readLevelId(fluent).writeLevelId(basic).speakLevelId(fluent).build());
        fresh();

        EmployeeLanguageResponse read = languages.getAll(emp).get(0);
        assertEquals("Arabic", read.getLanguageName());
        assertEquals("العربية", read.getLanguageNameAr());
        assertEquals("Fluent", read.getReadLevelName());
        assertEquals("Basic", read.getWriteLevelName());
        assertEquals("Fluent", read.getSpeakLevelName());

        // the same language twice is refused, also while the first record is switched off; another employee may have it
        assertEquals("DUPLICATE_LANGUAGE", assertThrows(BusinessRuleException.class, () -> languages.add(emp,
            EmployeeLanguageRequest.builder().languageId(languageId).build())).getRuleCode());
        languages.deactivate(emp, added.getId());
        fresh();
        assertFalse(languages.getAll(emp).get(0).getIsActive());
        assertThrows(BusinessRuleException.class, () -> languages.add(emp, EmployeeLanguageRequest.builder().languageId(languageId).build()));
        assertDoesNotThrow(() -> languages.add(other, EmployeeLanguageRequest.builder().languageId(languageId).build()));
        languages.activate(emp, added.getId());
        fresh();
        assertTrue(languages.getAll(emp).get(0).getIsActive());
    }

    @Test
    void anotherEmployeesRecordIsNotFound_andOnlyTheOwnersListShowsIt() {
        Long emp = employee("e");
        Long intruder = employee("f");
        Long id = references.add(emp, EmployeeReferenceRequest.builder().firstName("Mary").build()).getId();
        fresh();

        assertEquals(List.of(), references.getAll(intruder));
        assertThrows(ResourceNotFoundException.class, () -> references.deactivate(intruder, id));
        assertThrows(ResourceNotFoundException.class, () -> references.update(intruder, id,
            EmployeeReferenceRequest.builder().firstName("Hacked").version(0L).build()));
        fresh();
        assertEquals("Mary", references.getAll(emp).get(0).getFirstName());
        assertTrue(references.getAll(emp).get(0).getIsActive());
    }

    @Test
    void anUnknownEmployeeIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> dependants.getAll(987654321L));
    }
}
