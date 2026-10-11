package com.hrms.employee.repository;

import com.hrms.employee.DatabaseTest;
import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import com.hrms.employee.entity.Employee;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** The native and bulk queries in EmployeeRepository, run against a real Oracle database. */
@Transactional
class EmployeeRepositoryDbTest extends DatabaseTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired EmployeeRepository repo;
    @PersistenceContext EntityManager em;

    @Test
    void detailQueryReturnsTheEmployeeWithItsDatabaseAssignedCode() {
        Employee e = employee("Amira", "Nasser");

        var detail = repo.findDetailById(e.getId()).orElseThrow();

        assertEquals("Amira", detail.getFirstName());
        assertEquals(e.getEmployeeCode(), detail.getEmployeeCode());
        assertNotNull(detail.getEmployeeCode(), "the database trigger assigns the code");
        assertEquals(LocalDate.of(2024, 1, 15).toString(), detail.getHireDate());
        assertTrue(repo.findDetailById(-1L).isEmpty());
    }

    @Test
    void searchFindsByNameAndFiltersByStatusAndActiveFlag() {
        Employee e = employee("Zuberi", "Quartermain");
        var any = PageRequest.of(0, 10);

        var byName = repo.findAllWithFilters("quartermain", null, null, null, null, 1, any);
        assertEquals(1, byName.getTotalElements());
        assertEquals(e.getId(), byName.getContent().get(0).getEmployeeId());

        assertEquals(0, repo.findAllWithFilters("quartermain", null, "TERMINATED", null, null, 1, any).getTotalElements());
        assertEquals(0, repo.findAllWithFilters("quartermain", null, null, null, null, 0, any).getTotalElements());
    }

    @Test
    void searchTreatsAPercentSignLiterally() {
        employee("Percy", "Literal");
        var any = PageRequest.of(0, 10);

        // EmployeeServiceImpl escapes % as \% before calling the query; unescaped it would match everyone.
        assertEquals(0, repo.findAllWithFilters("\\%", null, null, null, null, 1, any).getTotalElements());
        assertTrue(repo.findAllWithFilters("%", null, null, null, null, 1, any).getTotalElements() >= 1);
    }

    @Test
    void deactivatingBumpsTheVersionSoAnOpenScreenIsRefused() {
        Employee e = employee("Hassan", "Versioned");
        long before = e.getVersion();

        int rows = repo.softDeleteById(e.getId(), "tester");

        assertEquals(1, rows);
        Employee reloaded = repo.findById(e.getId()).orElseThrow();
        assertFalse(reloaded.getIsActive());
        assertEquals(before + 1, reloaded.getVersion());
    }

    @Test
    void rowLockQueryWorks() {
        Employee e = employee("Lock", "Test");
        assertTrue(repo.findByIdForUpdate(e.getId()).isPresent());
    }

    /** Same shape as EmployeeServiceImpl.createEmployee: save, then refresh to read the generated code. */
    private Employee employee(String first, String last) {
        int n = SEQ.incrementAndGet();
        Employee e = repo.saveAndFlush(Employee.builder()
            .firstName(first).firstNameAr("ع" + n)
            .lastName(last).lastNameAr("ل" + n)
            .dateOfBirth(LocalDate.of(1990, 5, 1))
            .gender(Gender.FEMALE)
            .personalEmail("p" + n + "@example.test")
            .workEmail("w" + n + "@example.test")
            .hireDate(LocalDate.of(2024, 1, 15))
            .employmentType(EmploymentType.FULL_TIME)
            .build());
        em.refresh(e);
        return e;
    }
}
