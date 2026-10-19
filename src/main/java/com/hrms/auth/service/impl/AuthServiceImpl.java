package com.hrms.auth.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.auth.dto.request.ChangePasswordRequest;
import com.hrms.auth.dto.request.ResetPasswordRequest;
import com.hrms.auth.security.PasswordPolicy;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.auth.dto.request.LoginRequest;
import com.hrms.auth.dto.request.LogoutRequest;
import com.hrms.auth.dto.request.RefreshTokenRequest;
import com.hrms.auth.dto.response.LoginResponse;
import com.hrms.auth.dto.response.UserInfoResponse;
import com.hrms.auth.entity.AuthUser;
import com.hrms.auth.entity.RefreshToken;
import com.hrms.auth.repository.AuthUserRepository;
import com.hrms.auth.repository.RefreshTokenRepository;
import com.hrms.auth.security.UserDetailsServiceImpl;
import com.hrms.auth.service.AuthService;
import com.hrms.auth.service.LogText;
import com.hrms.common.exception.AccountDisabledException;
import com.hrms.common.exception.AccountLockedException;
import com.hrms.common.exception.InvalidTokenException;
import com.hrms.common.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthUserRepository     authUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider       jwtTokenProvider;   // from common-lib
  //  private final UserDetailsServiceImpl userDetailsService;
    private final AuthenticationManager  authenticationManager;
    private final PasswordEncoder        passwordEncoder;
    private final AuditTrail audit;

    // Property names match common-lib JwtTokenProvider and application.properties exactly
    @Value("${hrms.jwt.expiration-ms:1800000}")
    private long accessTokenExpiryMs;

    @Value("${hrms.jwt.refresh-expiration-ms:604800000}")
    private long refreshTokenExpiryMs;

    @Value("${hrms.jwt.rotate-refresh-token:true}")
    private boolean rotateRefreshToken;

    /** How long after a rotation a second request with the old cookie (another tab) still gets an access token. */
    @Value("${hrms.jwt.refresh-reuse-grace-seconds:10}")
    private long reuseGraceSeconds;

    /**
     * noRollbackFor is essential: a bad password increments the failed-attempts counter and
     * THEN throws. Without it the runtime exception rolls the increment back and the
     * 5-attempt lockout never takes effect.
     */
    @Override
    @Transactional(noRollbackFor = {BadCredentialsException.class, AccountLockedException.class})
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        // LoginRequest is a record — use record accessor methods
        String identifier = request.username().trim();
        // The row is locked until this transaction ends, so parallel guesses for one account run one after
        // another and the lock check below always sees the previous attempt's committed failure count.
        AuthUser user = authUserRepository.findByUsernameOrEmailForUpdate(identifier)
            .orElseThrow(() -> {
                // Same work as a real attempt, so response time does not reveal whether the user exists
                passwordEncoder.matches(request.password(), dummyHash());
                // Free text typed by an anonymous caller: strip control characters and cap the length
                String shown = LogText.clean(identifier);
                audit.recordAs(shown, "LOGIN_FAILED", "USER", shown, "unknown user");
                return new BadCredentialsException("Invalid username or password.");
            });
        log.info("Login attempt — user={}", user.getUsername());

        validateAccountState(user);

        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getUsername(), request.password())
            );
        } catch (BadCredentialsException ex) {
            authUserRepository.incrementFailedAttempts(user.getUserId(), LocalDateTime.now());
            int attempts = (user.getFailedAttempts() == null ? 0 : user.getFailedAttempts()) + 1;
            audit.recordAs(user.getUsername(), "LOGIN_FAILED", "USER", user.getUserId(), "wrong password, attempt " + attempts);
            if (attempts >= 5) {
                audit.recordAs(user.getUsername(), "ACCOUNT_LOCKED", "USER", user.getUserId(), "5 failed attempts");
                throw new AccountLockedException("Account locked after 5 failed attempts. Try again in 30 minutes.");
            }
            throw new BadCredentialsException("Invalid username or password.");
        } catch (LockedException ex) {
            throw new AccountLockedException("Account is locked. Please try again later.");
        } catch (DisabledException ex) {
            throw new AccountDisabledException("Your account has been disabled. Contact HR.");
        }

        authUserRepository.resetFailedAttemptsAndUpdateLastLogin(user.getUserId(), LocalDateTime.now());

        String accessToken = generateToken(user);
        RefreshToken rt    = createRefreshToken(user, httpRequest);

        audit.recordAs(user.getUsername(), "LOGIN_SUCCESS", "USER", user.getUserId(), null);
        log.info("Login OK — user={} role={}", user.getUsername(), user.getRole());
        return buildResponse(user, accessToken, rt.getToken());
    }

    /**
     * A refresh token works once. The conditional revoke decides a race: exactly one request rotates the token.
     * A request that finds it already used within a few seconds is another tab of the same browser, which shares
     * the winner's new cookie, so it gets an access token only. A used token presented later is refused and
     * audited. The replacement expires with the token it replaces, so rotation never extends a session.
     * noRollbackFor keeps the audit row when the call is refused.
     */
    @Override
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public LoginResponse refreshToken(RefreshTokenRequest request, HttpServletRequest httpRequest) {
        // RefreshTokenRequest is a record
        RefreshToken rt = refreshTokenRepository.findByToken(request.refreshToken())
            .orElseThrow(() -> new InvalidTokenException("Refresh token not found."));

        if (rt.isExpired()) {
            throw new InvalidTokenException("Refresh token expired. Please login again.");
        }

        AuthUser user = rt.getUser();
        validateAccountState(user);

        boolean alreadyUsed = Boolean.TRUE.equals(rt.getIsRevoked())
            || (rotateRefreshToken && refreshTokenRepository.revokeIfActive(request.refreshToken()) == 0);
        if (alreadyUsed) {
            return answerUsedToken(user);
        }

        String newAccess = generateToken(user);
        if (!rotateRefreshToken) {
            return buildResponse(user, newAccess, request.refreshToken());
        }
        String next = createRefreshToken(user, httpRequest, rt.getExpiryDate()).getToken();
        return buildResponse(user, newAccess, next);
    }

    /** Answer for a token that was already rotated or revoked: tolerate the same-moment tab, refuse a later replay. */
    private LoginResponse answerUsedToken(AuthUser user) {
        LocalDateTime graceStart = LocalDateTime.now().minusSeconds(reuseGraceSeconds);
        if (refreshTokenRepository.existsByUser_UserIdAndCreatedAtAfter(user.getUserId(), graceStart)) {
            return buildResponse(user, generateToken(user), null);
        }
        audit.recordAs(user.getUsername(), "REFRESH_TOKEN_REJECTED", "USER", user.getUserId(),
            "a revoked or already used refresh token was presented");
        throw new InvalidTokenException("Refresh token revoked. Please login again.");
    }

    @Override
    @Transactional
    public void logout(LogoutRequest request) {
        // LogoutRequest is a record with field `token`
        if (StringUtils.hasText(request.token())) {
            refreshTokenRepository.revokeByToken(request.token());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserInfoResponse validateToken(String bearerToken) {
        String token = bearerToken.startsWith("Bearer ") ? bearerToken.substring(7) : bearerToken;
        if (!jwtTokenProvider.validateToken(token))
            throw new InvalidTokenException("Token is invalid or expired.");

        // common-lib uses extractSubject() not extractUsername()
        String subject = jwtTokenProvider.extractSubject(token);
        AuthUser user  = authUserRepository.findByUsernameOrEmail(subject)
            .orElseThrow(() -> new InvalidTokenException("User not found."));
        return toUserInfo(user);
    }
    
    

    // ── Password management ───────────────────────────────────────────────────

    /** noRollbackFor: the wrong-current-password attempt counter must persist even though we throw. */
    @Override
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public void changePassword(String username, ChangePasswordRequest request) {
        AuthUser user = authUserRepository.findByUsernameOrEmailForUpdate(username)
            .orElseThrow(() -> new InvalidTokenException("User not found."));

        // The lock applies here too: an already-issued access token must not keep guessing the current password
        validateAccountState(user);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            // A stolen session must not be able to brute-force the current password for free
            authUserRepository.incrementFailedAttempts(user.getUserId(), LocalDateTime.now());
            log.warn("Change-password rejected (wrong current password) — user={}", user.getUsername());
            audit.recordAs(user.getUsername(), "PASSWORD_CHANGE_FAILED", "USER", user.getUserId(), "wrong current password");
            throw new BusinessRuleException("INVALID_CURRENT_PASSWORD", "Current password is incorrect.");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessRuleException("PASSWORD_UNCHANGED",
                "The new password must be different from the current password.");
        }
        PasswordPolicy.validate(request.newPassword());

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(0);
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setFailedAttempts(0);
        user.setIsLocked(false);
        user.setLockTime(null);
        user.setUpdatedBy(user.getUsername());
        authUserRepository.save(user);

        // Ends every other session; the caller signs in again with the new password
        refreshTokenRepository.revokeAllByUserId(user.getUserId());
        audit.recordAs(user.getUsername(), "PASSWORD_CHANGED", "USER", user.getUserId(), null);
        log.info("Password changed — user={}", user.getUsername());
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request, String adminUsername) {
        AuthUser user = authUserRepository.findByEmployeeId(request.employeeId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "User account", "employeeId", request.employeeId()));
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessRuleException("ACCOUNT_INACTIVE",
                "This account is deactivated; its password cannot be reset.");
        }
        PasswordPolicy.validate(request.temporaryPassword());

        user.setPasswordHash(passwordEncoder.encode(request.temporaryPassword()));
        user.setMustChangePassword(1);
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setIsLocked(false);
        user.setFailedAttempts(0);
        user.setLockTime(null);
        user.setUpdatedBy(adminUsername);
        authUserRepository.save(user);

        refreshTokenRepository.revokeAllByUserId(user.getUserId());
        audit.recordAs(adminUsername, "PASSWORD_RESET", "USER", user.getUserId(), "target " + user.getUsername());
        log.warn("Password reset by admin — admin={} target={}", adminUsername, user.getUsername());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String generateToken(AuthUser user) {
        // common-lib signature: generateAccessToken(String userUuid, String email,
        //                                            List<String> roles, Long employeeId)
        return jwtTokenProvider.generateAccessToken(
            user.getUsername(),
            user.getEmail(),
            List.of(user.getRole().name()),
            user.getEmployeeId()
        );
    }

    private RefreshToken createRefreshToken(AuthUser user, HttpServletRequest req) {
        return createRefreshToken(user, req, Instant.now().plusMillis(refreshTokenExpiryMs));
    }

    private RefreshToken createRefreshToken(AuthUser user, HttpServletRequest req, Instant expiry) {
        return refreshTokenRepository.save(RefreshToken.builder()
            .token(UUID.randomUUID().toString())
            .user(user)
            .expiryDate(expiry)
            .isRevoked(false)
            .userAgent(truncate(req.getHeader("User-Agent"), 500))
            .ipAddress(clientIp(req))
            .build());
    }

    private LoginResponse buildResponse(AuthUser user, String accessToken, String refreshToken) {
        return LoginResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .tokenType("Bearer")
            .expiresIn(accessTokenExpiryMs / 1000)
            .user(toUserInfo(user))
            .build();
    }

    private UserInfoResponse toUserInfo(AuthUser user) {
        String lastLogin = user.getLastLogin() != null
            ? user.getLastLogin().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null;
        return UserInfoResponse.builder()
            .id(user.getUserId())
            .username(user.getUsername())
            .email(user.getEmail())
            .fullName(user.getFullNameEn())
            .role(user.getRole())
            .department(user.getDepartment())
            .employeeId(user.getEmployeeCode())
            .avatarUrl(user.getAvatarUrl())
            .lastLogin(lastLogin)
            .mustChangePassword(user.getMustChangePassword() != null && user.getMustChangePassword() == 1)
            .build();
    }

    private void validateAccountState(AuthUser user) {
        if (!Boolean.TRUE.equals(user.getIsActive()))
            throw new AccountDisabledException("Your account has been disabled. Contact HR.");
        if (Boolean.TRUE.equals(user.getIsLocked()) && !user.isAccountNonLocked())
            throw new AccountLockedException("Account is locked. Try again in 30 minutes.");
    }

    /**
     * The connection's address. Behind the reverse proxy this is the real client only when
     * server.forward-headers-strategy=native is set, which honours X-Forwarded-For from the proxy
     * alone; reading the header here would let any caller write whatever IP they like.
     */
    private String clientIp(HttpServletRequest req) {
        return req.getRemoteAddr();
    }

    private volatile String dummyHash;

    private String dummyHash() {
        String h = dummyHash;
        if (h == null) {
            h = dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
        }
        return h;
    }

    private String truncate(String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max);
    }
}
