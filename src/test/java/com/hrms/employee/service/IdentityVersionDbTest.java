package com.hrms.employee.service;

import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import com.hrms.employee.DatabaseTest;
import com.hrms.employee.dto.request.IdentityInfoRequest;
import com.hrms.employee.dto.response.IdentityInfoResponse;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.EmployeeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The identity screen sends back the version it was given. Against a real schema: every save returns the version
 * the next save has to carry, so two saves in a row from the same screen work, and an older version is refused.
 */
@Transactional
class IdentityVersionDbTest extends DatabaseTest {

    @Autowired IdentityInfoService identity;
    @Autowired EmployeeRepository employeeRepository;
    @PersistenceContext EntityManager em;

    private void fresh() {
        em.flush();
        em.clear();
    }

    private Long employee() {
        Employee e = employeeRepository.saveAndFlush(Employee.builder()
            .firstName("Amira").firstNameAr("ع").lastName("Versioned").lastNameAr("ل")
            .dateOfBirth(LocalDate.of(1990, 5, 1)).gender(Gender.FEMALE)
            .personalEmail("idv-test-p@example.test").workEmail("idv-test-w@example.test")
            .hireDate(LocalDate.of(2024, 1, 15)).employmentType(EmploymentType.FULL_TIME).build());
        fresh();
        return e.getId();
    }

    private static IdentityInfoRequest request(String nationalId, Long version) {
        IdentityInfoRequest r = new IdentityInfoRequest();
        r.setNationalId(nationalId);
        r.setPassportNumber("P-" + nationalId);
        r.setVersion(version);
        return r;
    }

    @Test
    void eachSaveReturnsTheVersionTheNextSaveMustCarry() {
        Long emp = employee();

        IdentityInfoResponse created = identity.saveIdentityInfo(emp, request("ID-ONE-001", null));
        fresh();
        assertNotNull(created.getVersion());

        IdentityInfoResponse second = identity.saveIdentityInfo(emp, request("ID-ONE-002", created.getVersion()));
        fresh();
        assertEquals(created.getVersion() + 1, second.getVersion()); // the response is not one version behind

        // a third save with the version the second one returned works, without reloading the screen
        IdentityInfoResponse third = identity.saveIdentityInfo(emp, request("ID-ONE-003", second.getVersion()));
        fresh();
        assertEquals(second.getVersion() + 1, third.getVersion());
    }

    @Test
    void anOlderVersionIsRefused() {
        Long emp = employee();
        IdentityInfoResponse created = identity.saveIdentityInfo(emp, request("ID-TWO-001", null));
        fresh();
        identity.saveIdentityInfo(emp, request("ID-TWO-002", created.getVersion()));
        fresh();

        assertThrows(OptimisticLockingFailureException.class,
            () -> identity.saveIdentityInfo(emp, request("ID-TWO-003", created.getVersion())));
    }
}
