package com.hrms.employee.repository;

import com.hrms.auth.entity.AuthUser;
import com.hrms.auth.entity.RefreshToken;
import com.hrms.auth.repository.AuthUserRepository;
import com.hrms.auth.repository.RefreshTokenRepository;
import com.hrms.common.enums.UserRole;
import com.hrms.employee.DatabaseTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** The conditional revoke, the grace lookup and the cleanup rule of RefreshTokenRepository, on a real Oracle database. */
@Transactional
class RefreshTokenRepositoryDbTest extends DatabaseTest {

    @Autowired AuthUserRepository users;
    @Autowired RefreshTokenRepository tokens;
    @PersistenceContext EntityManager em;

    private AuthUser user() {
        return users.saveAndFlush(AuthUser.builder().username("rtuser").email("rtuser@example.com").passwordHash("hash")
            .role(UserRole.EMPLOYEE).fullNameEn("RT User").isActive(true).isLocked(false)
            .failedAttempts(0).mustChangePassword(0).build());
    }

    private RefreshToken token(AuthUser u, String value, Instant expiry, boolean revoked) {
        return tokens.saveAndFlush(RefreshToken.builder().token(value).user(u).expiryDate(expiry).isRevoked(revoked).build());
    }

    @Test
    void onlyTheFirstCallerWinsTheRevoke() {
        token(user(), "t-1", Instant.now().plusSeconds(3600), false);

        assertEquals(1, tokens.revokeIfActive("t-1"));
        assertEquals(0, tokens.revokeIfActive("t-1"), "a second caller must lose");
        assertEquals(0, tokens.revokeIfActive("no-such-token"));
    }

    @Test
    void theGraceLookupSeesATokenIssuedJustNowButNotOneFromBefore() {
        AuthUser u = user();
        token(u, "t-2", Instant.now().plusSeconds(3600), false);

        assertTrue(tokens.existsByUser_UserIdAndCreatedAtAfter(u.getUserId(), LocalDateTime.now().minusSeconds(60)));
        assertFalse(tokens.existsByUser_UserIdAndCreatedAtAfter(u.getUserId(), LocalDateTime.now().plusSeconds(60)));
    }

    @Test
    void cleanupDeletesExpiredTokensButKeepsRevokedOnesUntilTheyExpire() {
        AuthUser u = user();
        token(u, "expired", Instant.now().minusSeconds(60), true);
        token(u, "revoked-live", Instant.now().plusSeconds(3600), true);
        token(u, "active", Instant.now().plusSeconds(3600), false);

        assertEquals(1, tokens.deleteExpired(Instant.now()));
        em.clear();

        assertTrue(tokens.findByToken("expired").isEmpty());
        assertTrue(tokens.findByToken("revoked-live").isPresent(), "kept so a replay can be recognised");
        assertTrue(tokens.findByToken("active").isPresent());
    }
}
