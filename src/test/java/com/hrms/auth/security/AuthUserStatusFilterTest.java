package com.hrms.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.auth.entity.AuthUser;
import com.hrms.auth.repository.AuthUserRepository;
import com.hrms.common.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** F7: a token issued before the last password change/reset must be rejected. */
class AuthUserStatusFilterTest {

    private final AuthUserRepository repo = mock(AuthUserRepository.class);
    private final AuthUserStatusFilter filter = new AuthUserStatusFilter(repo, new ObjectMapper());

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /** Runs one request carrying a token with the given iat; returns the response status. */
    private int run(LocalDateTime passwordChangedAt, long iat) throws Exception {
        AuthUser user = AuthUser.builder().username("u").role(UserRole.EMPLOYEE)
                .passwordChangedAt(passwordChangedAt).build();
        when(repo.findByUsernameOrEmail("u")).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "u", null, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))));

        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"iat\":" + iat + "}").getBytes());
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer h." + payload + ".s");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res.getStatus();
    }

    private static long epoch(LocalDateTime t) {
        return t.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    @Test
    void tokenIssuedBeforePasswordChangeIsRejected() throws Exception {
        LocalDateTime changed = LocalDateTime.now();
        assertEquals(401, run(changed, epoch(changed) - 60));
    }

    @Test
    void tokenIssuedAfterPasswordChangeIsAccepted() throws Exception {
        LocalDateTime changed = LocalDateTime.now();
        assertEquals(200, run(changed, epoch(changed) + 60));
    }

    @Test
    void accountThatNeverChangedPasswordIsUnaffected() throws Exception {
        assertEquals(200, run(null, 1));
    }
}
