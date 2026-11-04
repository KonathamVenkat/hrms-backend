package com.hrms.employee.service;

import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import com.hrms.employee.DatabaseTest;
import com.hrms.employee.dto.request.EmployeeExperienceRequest;
import com.hrms.employee.dto.request.EmployeeReferenceRequest;
import com.hrms.employee.dto.response.EmployeeCompletenessResponse;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.EmployeeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The completeness query against a real Oracle schema built from db/migration: every table and column name in the
 * one SELECT is valid, the active-only rule holds (a deactivated record does not count), and the qualifications and
 * experience tabs need all of their sections.
 */
@Transactional
class EmployeeCompletenessDbTest extends DatabaseTest {

    @Autowired EmployeeCompletenessService completeness;
    @Autowired EmployeeExperienceService experience;
    @Autowired EmployeeReferenceService references;
    @Autowired EmployeeRepository employeeRepository;
    @PersistenceContext EntityManager em;

    private void fresh() {
        em.flush();
        em.clear();
    }

    private Long employee(String tag) {
        Employee e = employeeRepository.saveAndFlush(Employee.builder()
            .firstName("Amira").firstNameAr("ع").lastName("Complete" + tag).lastNameAr("ل")
            .dateOfBirth(LocalDate.of(1990, 5, 1)).gender(Gender.FEMALE)
            .personalEmail("cmp-test-p-" + tag + "@example.test").workEmail("cmp-test-w-" + tag + "@example.test")
            .hireDate(LocalDate.of(2024, 1, 15)).employmentType(EmploymentType.FULL_TIME).build());
        fresh();
        return e.getId();
    }

    @Test
    void aNewEmployeeHasNothingFilledIn() {
        Long emp = employee("a");

        EmployeeCompletenessResponse r = completeness.getCompleteness(emp);

        assertFalse(r.job());
        assertFalse(r.addresses());
        assertFalse(r.identity());
        assertFalse(r.bank());
        assertFalse(r.documents());
        assertFalse(r.family());
        assertFalse(r.qualifications());
        assertFalse(r.experience());
    }

    @Test
    void experienceNeedsBothPastEmployersAndReferences() {
        Long emp = employee("c");

        experience.add(emp, EmployeeExperienceRequest.builder().employerName("Ministry of Petroleum")
            .startDate(LocalDate.of(2012, 7, 1)).build());
        fresh();
        assertFalse(completeness.getCompleteness(emp).experience());

        references.add(emp, EmployeeReferenceRequest.builder().firstName("John").lastName("Deng")
            .phone("+211 912 111 111").build());
        fresh();
        assertTrue(completeness.getCompleteness(emp).experience());
    }

    @Test
    void aDeactivatedRecordDoesNotCount() {
        Long emp = employee("b");
        experience.add(emp, EmployeeExperienceRequest.builder().employerName("Ministry of Petroleum")
            .startDate(LocalDate.of(2012, 7, 1)).build());
        Long reference = references.add(emp, EmployeeReferenceRequest.builder().firstName("John").lastName("Deng")
            .phone("+211 912 111 111").build()).getId();
        fresh();
        assertTrue(completeness.getCompleteness(emp).experience());

        references.deactivate(emp, reference);
        fresh();
        assertFalse(completeness.getCompleteness(emp).experience()); // the only reference is switched off

        references.activate(emp, reference);
        fresh();
        assertTrue(completeness.getCompleteness(emp).experience());
    }

    @Test
    void anUnknownEmployeeIsNotFound() {
        org.junit.jupiter.api.Assertions.assertThrows(com.hrms.common.exception.ResourceNotFoundException.class,
            () -> completeness.getCompleteness(-1L));
    }
}
