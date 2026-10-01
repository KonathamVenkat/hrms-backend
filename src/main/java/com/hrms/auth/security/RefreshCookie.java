package com.hrms.auth.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * The refresh token travels in an HttpOnly cookie instead of the page's storage, so a script
 * injected into the page cannot read it. The cookie is limited to the auth endpoints, is not sent
 * with cross-site requests (SameSite) and, outside local development, only over HTTPS (Secure).
 */
@Component
public class RefreshCookie {

    private final String name;
    private final boolean secure;
    private final String sameSite;
    private final String path;
    private final long refreshExpiryMs;

    public RefreshCookie(
            @Value("${hrms.auth.refresh-cookie.name:hrms_refresh}") String name,
            @Value("${hrms.auth.refresh-cookie.secure:true}") boolean secure,
            @Value("${hrms.auth.refresh-cookie.same-site:Strict}") String sameSite,
            @Value("${hrms.auth.refresh-cookie.path:/api/v1/auth}") String path,
            @Value("${hrms.jwt.refresh-expiration-ms:604800000}") long refreshExpiryMs) {
        this.name = name;
        this.secure = secure;
        this.sameSite = sameSite;
        this.path = path;
        this.refreshExpiryMs = refreshExpiryMs;
    }

    /** The Set-Cookie header value that stores {@code token} for the lifetime of the refresh token. */
    public String create(String token) {
        return base(token).maxAge(Duration.ofMillis(refreshExpiryMs)).build().toString();
    }

    /** The Set-Cookie header value that removes the cookie from the browser. */
    public String clear() {
        return base("").maxAge(Duration.ZERO).build().toString();
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite(sameSite)
            .path(path);
    }
}
