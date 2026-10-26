package com.hrms.employee.repository;

import com.hrms.auth.entity.AuthUser;
import com.hrms.auth.repository.AuthUserRepository;
import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import com.hrms.common.enums.UserRole;
import com.hrms.employee.DatabaseTest;
import com.hrms.employee.entity.Employee;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The row-locking login lookup, run against a real Oracle database (FOR UPDATE is only produced at execution). */
@Transactional
class AuthUserRepositoryDbTest extends DatabaseTest {

    @Autowired AuthUserRepository repo;
    @Autowired EmployeeRepository employees;

    private AuthUser save(String username, String email, boolean active) {
        return repo.saveAndFlush(AuthUser.builder().username(username).email(email).passwordHash("hash")
            .role(UserRole.EMPLOYEE).fullNameEn("Test User").isActive(active).isLocked(false)
            .failedAttempts(0).mustChangePassword(0).build());
    }

    @Test
    void theLockingLookupFindsAnActiveUserByUsernameOrEmailIgnoringCase() {
        AuthUser u = save("lockuser", "lockuser@example.com", true);

        assertEquals(u.getUserId(), repo.findByUsernameOrEmailForUpdate("LockUser").orElseThrow().getUserId());
        assertEquals(u.getUserId(), repo.findByUsernameOrEmailForUpdate("LOCKUSER@example.com").orElseThrow().getUserId());
    }

    /** Relative to what the database already holds, so other GENERAL_MANAGER rows do not matter. */
    @Test
    void otherActiveHoldersOfARoleLeaveOutTheEmployeeThemselfAndDeactivatedLogins() {
        String run = Long.toString(System.nanoTime(), 36);
        long a = employee("a", run), b = employee("b", run), c = employee("c", run);
        long before = repo.countOtherActiveHolders(UserRole.GENERAL_MANAGER, a);

        login(a, run, true);
        login(c, run, false);

        assertEquals(before, repo.countOtherActiveHolders(UserRole.GENERAL_MANAGER, a), "own login");
        assertEquals(before + 1, repo.countOtherActiveHolders(UserRole.GENERAL_MANAGER, b), "a counts, c is inactive");
    }

    private long employee(String tag, String run) {
        return employees.saveAndFlush(Employee.builder()
            .employeeCode("AU-" + tag + "-" + run).firstName("Au").firstNameAr("ع").lastName(tag).lastNameAr("ل")
            .dateOfBirth(LocalDate.of(1990, 1, 1)).gender(Gender.MALE)
            .personalEmail("au-" + tag + "-" + run + "-p@example.test")
            .workEmail("au-" + tag + "-" + run + "-w@example.test")
            .hireDate(LocalDate.of(2024, 1, 1)).employmentType(EmploymentType.FULL_TIME).build()).getId();
    }

    private void login(long employeeId, String run, boolean active) {
        repo.saveAndFlush(AuthUser.builder().username("au-" + employeeId + "-" + run)
            .email("au-" + employeeId + "-" + run + "@example.test").passwordHash("hash")
            .role(UserRole.GENERAL_MANAGER).employeeId(employeeId).fullNameEn("Test User").isActive(active)
            .isLocked(false).failedAttempts(0).mustChangePassword(0).build());
    }

    @Test
    void theLockingLookupDoesNotReturnADeactivatedUser() {
        save("gone", "gone@example.com", false);

        assertTrue(repo.findByUsernameOrEmailForUpdate("gone").isEmpty());
    }
}
