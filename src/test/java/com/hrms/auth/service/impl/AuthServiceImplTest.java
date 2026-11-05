package com.hrms.auth.service.impl;

import com.hrms.auth.dto.request.ChangePasswordRequest;
import com.hrms.auth.dto.request.LoginRequest;
import com.hrms.auth.dto.request.RefreshTokenRequest;
import com.hrms.auth.dto.response.LoginResponse;
import com.hrms.auth.entity.AuthUser;
import com.hrms.auth.entity.RefreshToken;
import com.hrms.auth.repository.AuthUserRepository;
import com.hrms.auth.repository.RefreshTokenRepository;
import com.hrms.auth.service.AuditTrail;
import com.hrms.common.enums.UserRole;
import com.hrms.common.exception.AccountLockedException;
import com.hrms.common.exception.AccountDisabledException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.InvalidTokenException;
import com.hrms.common.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Lockout rules of login and change-password: the lock must gate every password comparison, and both paths
 * must read the account through the row-locking lookup so parallel guesses queue up.
 */
class AuthServiceImplTest {

    AuthUserRepository users = mock(AuthUserRepository.class);
    RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    AuthenticationManager authentication = mock(AuthenticationManager.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    AuditTrail audit = mock(AuditTrail.class);
    HttpServletRequest http = mock(HttpServletRequest.class);

    JwtTokenProvider jwt = mock(JwtTokenProvider.class);

    AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(users, refreshTokens, jwt, authentication, encoder, audit);
        ReflectionTestUtils.setField(service, "rotateRefreshToken", true);
        ReflectionTestUtils.setField(service, "reuseGraceSeconds", 10L);
        ReflectionTestUtils.setField(service, "refreshTokenExpiryMs", 604_800_000L);
        when(jwt.generateAccessToken(any(), any(), any(), any())).thenReturn("new-access");
        when(refreshTokens.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));
    }

    private AuthUser user(boolean locked, LocalDateTime lockTime, int failedAttempts) {
        return AuthUser.builder().userId(1L).username("jaya").email("jaya@example.com").passwordHash("hash")
            .role(UserRole.EMPLOYEE).fullNameEn("Jaya").isActive(true).isLocked(locked).lockTime(lockTime)
            .failedAttempts(failedAttempts).mustChangePassword(0).build();
    }

    // ── change-password ─────────────────────────────────────────

    @Test
    void changePasswordIsRefusedForALockedAccountBeforeAnyGuess() {
        when(users.findByUsernameOrEmailForUpdate("jaya")).thenReturn(Optional.of(user(true, LocalDateTime.now(), 5)));

        assertThrows(AccountLockedException.class,
            () -> service.changePassword("jaya", new ChangePasswordRequest("guess", "NewPass123")));

        verify(encoder, never()).matches(any(), any());
        verify(users, never()).incrementFailedAttempts(any(), any());
    }

    @Test
    void changePasswordReadsTheAccountThroughTheRowLockingLookup() {
        when(users.findByUsernameOrEmailForUpdate("jaya")).thenReturn(Optional.of(user(false, null, 0)));
        when(encoder.matches("wrong", "hash")).thenReturn(false);

        assertThrows(BusinessRuleException.class,
            () -> service.changePassword("jaya", new ChangePasswordRequest("wrong", "NewPass123")));

        verify(users).findByUsernameOrEmailForUpdate("jaya");
        verify(users, never()).findByUsernameOrEmail(any());
    }

    @Test
    void aWrongCurrentPasswordStillCountsAsAFailedAttempt() {
        when(users.findByUsernameOrEmailForUpdate("jaya")).thenReturn(Optional.of(user(false, null, 2)));
        when(encoder.matches("wrong", "hash")).thenReturn(false);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.changePassword("jaya", new ChangePasswordRequest("wrong", "NewPass123")));

        assertEquals("INVALID_CURRENT_PASSWORD", ex.getRuleCode());
        verify(users).incrementFailedAttempts(eq(1L), any());
    }

    @Test
    void aSuccessfulChangeClearsAnExpiredLockAndEndsOtherSessions() {
        AuthUser expiredLock = user(true, LocalDateTime.now().minusMinutes(31), 5);
        when(users.findByUsernameOrEmailForUpdate("jaya")).thenReturn(Optional.of(expiredLock));
        when(encoder.matches("OldPass123", "hash")).thenReturn(true);
        when(encoder.encode("NewPass123")).thenReturn("newhash");

        service.changePassword("jaya", new ChangePasswordRequest("OldPass123", "NewPass123"));

        assertEquals("newhash", expiredLock.getPasswordHash());
        assertEquals(0, expiredLock.getFailedAttempts());
        assertFalse(expiredLock.getIsLocked());
        assertNull(expiredLock.getLockTime());
        verify(refreshTokens).revokeAllByUserId(1L);
    }

    // ── login ───────────────────────────────────────────────────

    @Test
    void loginIsRefusedForALockedAccountBeforeAnyPasswordCheck() {
        when(users.findByUsernameOrEmailForUpdate("jaya")).thenReturn(Optional.of(user(true, LocalDateTime.now(), 5)));

        assertThrows(AccountLockedException.class, () -> service.login(new LoginRequest("jaya", "guess"), http));

        verify(authentication, never()).authenticate(any());
    }

    @Test
    void loginReadsTheAccountThroughTheRowLockingLookupAndCountsAFailure() {
        when(users.findByUsernameOrEmailForUpdate("jaya")).thenReturn(Optional.of(user(false, null, 0)));
        when(authentication.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThrows(BadCredentialsException.class, () -> service.login(new LoginRequest("jaya", "guess"), http));

        verify(users).findByUsernameOrEmailForUpdate("jaya");
        verify(users, never()).findByUsernameOrEmail(any());
        verify(users).incrementFailedAttempts(eq(1L), any());
    }

    @Test
    void theFifthWrongPasswordLocksTheAccount() {
        when(users.findByUsernameOrEmailForUpdate("jaya")).thenReturn(Optional.of(user(false, null, 4)));
        when(authentication.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThrows(AccountLockedException.class, () -> service.login(new LoginRequest("jaya", "guess"), http));

        verify(audit).recordAs(eq("jaya"), eq("ACCOUNT_LOCKED"), eq("USER"), eq(1L), any());
    }

    @Test
    void anUnknownUsernameIsAuditedWithoutControlCharacters() {
        when(users.findByUsernameOrEmailForUpdate("abc\r\nINFO forged")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class,
            () -> service.login(new LoginRequest("abc\r\nINFO forged", "guess"), http));

        verify(audit).recordAs(eq("abc??INFO forged"), eq("LOGIN_FAILED"), eq("USER"), eq("abc??INFO forged"), eq("unknown user"));
    }

    // ── refresh-token rotation ──────────────────────────────────

    private RefreshToken storedToken(boolean revoked, Instant expiry) {
        return RefreshToken.builder().token("old-token").user(user(false, null, 0)).expiryDate(expiry).isRevoked(revoked).build();
    }

    private LoginResponse refresh() {
        return service.refreshToken(new RefreshTokenRequest("old-token"), http);
    }

    @Test
    void aFirstRefreshRotatesTheTokenAndKeepsTheOriginalExpiry() {
        Instant expiry = Instant.now().plusSeconds(3 * 24 * 3600);
        when(refreshTokens.findByToken("old-token")).thenReturn(Optional.of(storedToken(false, expiry)));
        when(refreshTokens.revokeIfActive(eq("old-token"), any())).thenReturn(1);

        LoginResponse response = refresh();

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(saved.capture());
        assertEquals(expiry, saved.getValue().getExpiryDate(), "rotation must not extend the session");
        assertEquals(saved.getValue().getToken(), response.refreshToken());
        assertNotEquals("old-token", response.refreshToken());
        assertEquals("new-access", response.accessToken());
    }

    @Test
    void theLoserOfARaceGetsAnAccessTokenOnlyAndNoSecondRefreshToken() {
        when(refreshTokens.findByToken("old-token")).thenReturn(Optional.of(storedToken(false, Instant.now().plusSeconds(3600))));
        when(refreshTokens.revokeIfActive(eq("old-token"), any())).thenReturn(0);   // another request rotated it first
        when(refreshTokens.findRotatedAt("old-token")).thenReturn(Optional.of(LocalDateTime.now().minusSeconds(2)));

        LoginResponse response = refresh();

        assertEquals("new-access", response.accessToken());
        assertNull(response.refreshToken(), "no second live refresh token may be issued");
        verify(refreshTokens, never()).save(any());
        verify(audit, never()).recordAs(any(), any(), any(), any(), any());
    }

    @Test
    void anAlreadyRotatedTokenSeenWithinTheGraceWindowIsTolerated() {
        when(refreshTokens.findByToken("old-token")).thenReturn(Optional.of(storedToken(true, Instant.now().plusSeconds(3600))));
        when(refreshTokens.findRotatedAt("old-token")).thenReturn(Optional.of(LocalDateTime.now().minusSeconds(2)));

        LoginResponse response = refresh();

        assertNull(response.refreshToken());
        verify(refreshTokens, never()).revokeIfActive(any(), any());
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void aUsedTokenPresentedLaterIsRefusedAndAudited() {
        when(refreshTokens.findByToken("old-token")).thenReturn(Optional.of(storedToken(true, Instant.now().plusSeconds(3600))));
        when(refreshTokens.findRotatedAt("old-token")).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, this::refresh);

        verify(audit).recordAs(eq("jaya"), eq("REFRESH_TOKEN_REJECTED"), eq("USER"), eq(1L), any());
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void aLostRaceOutsideTheGraceWindowIsRefused() {
        when(refreshTokens.findByToken("old-token")).thenReturn(Optional.of(storedToken(false, Instant.now().plusSeconds(3600))));
        when(refreshTokens.revokeIfActive(eq("old-token"), any())).thenReturn(0);
        when(refreshTokens.findRotatedAt("old-token")).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, this::refresh);

        verify(refreshTokens, never()).save(any());
    }

    @Test
    void aTokenRevokedAnyOtherWayGetsNoGrace_evenIfTheUserLoggedInASecondAgo() {
        // Revoked by logout or a password change: it was never rotated, so there is no rotation time to find.
        when(refreshTokens.findByToken("old-token")).thenReturn(Optional.of(storedToken(true, Instant.now().plusSeconds(3600))));
        when(refreshTokens.findRotatedAt("old-token")).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, this::refresh);
        verify(audit).recordAs(eq("jaya"), eq("REFRESH_TOKEN_REJECTED"), eq("USER"), eq(1L), any());
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void aTokenRotatedLongAgoGetsNoGrace() {
        when(refreshTokens.findByToken("old-token")).thenReturn(Optional.of(storedToken(true, Instant.now().plusSeconds(3600))));
        when(refreshTokens.findRotatedAt("old-token")).thenReturn(Optional.of(LocalDateTime.now().minusMinutes(5)));

        assertThrows(InvalidTokenException.class, this::refresh);
    }

    // ── admin reset-password ────────────────────────────────────

    private AuthUser userWithRole(UserRole role) {
        AuthUser u = user(false, null, 0);
        u.setRole(role);
        u.setEmployeeId(77L);
        return u;
    }

    @Test
    void anHrAdminCanResetAnEmployeeAccount() {
        AuthUser target = userWithRole(UserRole.EMPLOYEE);
        when(users.findByEmployeeId(77L)).thenReturn(Optional.of(target));
        when(encoder.encode("TempPass123")).thenReturn("new-hash");

        service.resetPassword(new com.hrms.auth.dto.request.ResetPasswordRequest(77L, "TempPass123"), "admin");

        assertEquals("new-hash", target.getPasswordHash());
        assertEquals(1, target.getMustChangePassword());
        verify(refreshTokens).revokeAllByUserId(1L);
        verify(audit).recordAs(eq("admin"), eq("PASSWORD_RESET"), eq("USER"), eq(1L), any());
    }

    @Test
    void anHrAdminCannotResetAnyOtherRole_andNothingChanges() {
        for (UserRole role : UserRole.values()) {
            if (role == UserRole.EMPLOYEE) continue;
            AuthUser target = userWithRole(role);
            when(users.findByEmployeeId(77L)).thenReturn(Optional.of(target));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.resetPassword(new com.hrms.auth.dto.request.ResetPasswordRequest(77L, "TempPass123"), "admin"),
                "role " + role);

            assertEquals("RESET_NOT_ALLOWED", ex.getRuleCode(), "role " + role);
            assertEquals("hash", target.getPasswordHash(), "role " + role);
        }
        verify(encoder, never()).encode(any());
        verify(refreshTokens, never()).revokeAllByUserId(any());
        verify(users, never()).save(any());
    }

    @Test
    void anExpiredTokenIsRefusedWithoutTouchingTheDatabase() {
        when(refreshTokens.findByToken("old-token")).thenReturn(Optional.of(storedToken(false, Instant.now().minusSeconds(1))));

        assertThrows(InvalidTokenException.class, this::refresh);

        verify(refreshTokens, never()).revokeIfActive(any(), any());
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void aDeactivatedAccountCannotRefresh() {
        RefreshToken token = storedToken(false, Instant.now().plusSeconds(3600));
        token.getUser().setIsActive(false);
        when(refreshTokens.findByToken("old-token")).thenReturn(Optional.of(token));

        assertThrows(AccountDisabledException.class, this::refresh);

        verify(refreshTokens, never()).revokeIfActive(any(), any());
    }

    @Test
    void withRotationOffTheSameTokenIsReturnedAndNothingIsRevoked() {
        ReflectionTestUtils.setField(service, "rotateRefreshToken", false);
        when(refreshTokens.findByToken("old-token")).thenReturn(Optional.of(storedToken(false, Instant.now().plusSeconds(3600))));

        LoginResponse response = refresh();

        assertEquals("old-token", response.refreshToken());
        verify(refreshTokens, never()).revokeIfActive(any(), any());
        verify(refreshTokens, never()).save(any());
    }
}
