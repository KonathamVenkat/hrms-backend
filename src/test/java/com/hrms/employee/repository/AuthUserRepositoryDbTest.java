package com.hrms.employee.repository;

import com.hrms.auth.entity.AuthUser;
import com.hrms.auth.repository.AuthUserRepository;
import com.hrms.common.enums.UserRole;
import com.hrms.employee.DatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The row-locking login lookup, run against a real Oracle database (FOR UPDATE is only produced at execution). */
@Transactional
class AuthUserRepositoryDbTest extends DatabaseTest {

    @Autowired AuthUserRepository repo;

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

    @Test
    void theLockingLookupDoesNotReturnADeactivatedUser() {
        save("gone", "gone@example.com", false);

        assertTrue(repo.findByUsernameOrEmailForUpdate("gone").isEmpty());
    }
}
