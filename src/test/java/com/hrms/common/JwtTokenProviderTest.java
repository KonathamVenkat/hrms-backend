package com.hrms.common;

import com.hrms.common.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** validateToken must answer false, never throw, for every kind of bad token. */
class JwtTokenProviderTest {

    private static JwtTokenProvider provider(char fill, long expiryMs) {
        JwtTokenProvider p = new JwtTokenProvider();
        String secret = Base64.getEncoder().encodeToString(String.valueOf(fill).repeat(40).getBytes());
        ReflectionTestUtils.setField(p, "jwtSecret", secret);
        ReflectionTestUtils.setField(p, "jwtExpirationMs", expiryMs);
        ReflectionTestUtils.setField(p, "refreshExpirationMs", 60_000L);
        return p;
    }

    private static String token(JwtTokenProvider p) {
        return p.generateAccessToken("jaya", "jaya@example.com", List.of("EMPLOYEE"), 7L);
    }

    @Test
    void aTokenSignedWithTheSameKeyIsValid() {
        JwtTokenProvider p = provider('a', 60_000L);
        assertTrue(p.validateToken(token(p)));
    }

    @Test
    void aTokenSignedWithAnotherKeyIsRejectedWithoutAnException() {
        String forged = token(provider('b', 60_000L));
        assertFalse(assertDoesNotThrow(() -> provider('a', 60_000L).validateToken(forged)));
    }

    @Test
    void anExpiredTokenIsRejected() {
        JwtTokenProvider p = provider('a', -1_000L);
        assertFalse(p.validateToken(token(p)));
    }

    @Test
    void malformedAndEmptyTokensAreRejectedWithoutAnException() {
        JwtTokenProvider p = provider('a', 60_000L);
        assertFalse(assertDoesNotThrow(() -> p.validateToken("not.a.jwt")));
        assertFalse(assertDoesNotThrow(() -> p.validateToken("abc\r\nINFO forged")));
        assertFalse(assertDoesNotThrow(() -> p.validateToken("")));
    }
}
