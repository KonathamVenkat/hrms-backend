package com.hrms.common.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JWT utility component used by all HRMS microservices.
 *
 * <p>Placed in {@code common-lib} so that:
 * <ul>
 *   <li>{@code auth-service} uses it to <b>generate</b> tokens on login.</li>
 *   <li>Every other microservice uses it to <b>validate</b> incoming tokens
 *       in their {@code JwtAuthenticationFilter}.</li>
 * </ul>
 * All services share the same secret key (configured via environment variable
 * {@code HRMS_JWT_SECRET}) so tokens issued by auth-service are accepted everywhere.</p>
 *
 * <h3>Token structure (claims):</h3>
 * <ul>
 *   <li>{@code sub}     — user UUID (stored as CREATED_BY / UPDATED_BY in Oracle)</li>
 *   <li>{@code email}   — user's work email</li>
 *   <li>{@code roles}   — list of role names (e.g., ["HR_ADMIN", "EMPLOYEE"])</li>
 *   <li>{@code empId}   — linked employee ID (null for system/admin users)</li>
 *   <li>{@code iat}     — issued-at timestamp</li>
 *   <li>{@code exp}     — expiry timestamp</li>
 * </ul>
 */
@Slf4j
@Component
public class JwtTokenProvider {

    // Inject from application properties / environment
    @Value("${hrms.jwt.secret}")
    private String jwtSecret;

    @Value("${hrms.jwt.expiration-ms:86400000}")   // default: 24 hours
    private long jwtExpirationMs;

    @Value("${hrms.jwt.refresh-expiration-ms:604800000}")  // default: 7 days
    private long refreshExpirationMs;

    /**
     * Fails application startup (rather than the first login) when the secret is
     * missing, not Base64, or shorter than 256 bits. The secret value is never logged.
     */
    @PostConstruct
    void validateSecret() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                "hrms.jwt.secret (env HRMS_JWT_SECRET) is not set");
        }
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtSecret);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("hrms.jwt.secret must be Base64-encoded");
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                "hrms.jwt.secret must decode to at least 32 bytes (256 bits)");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Token generation
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Generates an access token from a Spring Security {@link Authentication} object.
     *
     * @param authentication  the authenticated principal (populated by auth-service login)
     * @return                signed JWT access token string
     */
    public String generateAccessToken(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        List<String> roles = extractRoles(authentication.getAuthorities());
        return buildToken(userDetails.getUsername(), roles, jwtExpirationMs, null);
    }

    /**
     * Generates an access token with extra HRMS-specific claims.
     *
     * @param userUuid    the user's UUID (becomes JWT {@code sub})
     * @param email       the user's work email
     * @param roles       list of role strings (e.g., ["HR_ADMIN"])
     * @param employeeId  the linked employee ID (may be null for admin users)
     * @return            signed JWT access token string
     */
    public String generateAccessToken(String userUuid, String email,
                                      List<String> roles, Long employeeId) {
        Map<String, Object> extraClaims = Map.of(
            "email",  email,
            "empId",  employeeId != null ? employeeId : ""
        );
        return buildToken(userUuid, roles, jwtExpirationMs, extraClaims);
    }

    /**
     * Generates a long-lived refresh token. Contains only the subject (UUID)
     * and a {@code type=REFRESH} claim to prevent refresh tokens from being
     * used as access tokens.
     */
    public String generateRefreshToken(String userUuid) {
        return Jwts.builder()
            .subject(userUuid)
            .claim("type", "REFRESH")
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + refreshExpirationMs))
            .signWith(getSigningKey())
            .compact();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Token validation
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Validates the token's signature, expiry, and structure.
     *
     * @param token  the raw JWT string (without "Bearer " prefix)
     * @return       {@code true} if the token is valid and non-expired
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException ex) {
            log.warn("JWT token is expired: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.warn("JWT token is unsupported: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            log.warn("JWT token is malformed: {}", ex.getMessage());
        } catch (SecurityException ex) {
            log.warn("JWT signature validation failed: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.warn("JWT token compact string is empty or null: {}", ex.getMessage());
        }
        return false;
    }

    /**
     * Returns {@code true} if the token is a refresh token (has {@code type=REFRESH} claim).
     */
    public boolean isRefreshToken(String token) {
        String type = extractAllClaims(token).get("type", String.class);
        return "REFRESH".equals(type);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Claims extraction
    // ──────────────────────────────────────────────────────────────────────────

    /** Extracts the subject (user UUID) from the token. */
    public String extractSubject(String token) {
        return extractAllClaims(token).getSubject();
    }

    /** Extracts the user's work email from custom {@code email} claim. */
    public String extractEmail(String token) {
        return extractAllClaims(token).get("email", String.class);
    }

    /** Extracts the list of roles from the {@code roles} claim. */
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return extractAllClaims(token).get("roles", List.class);
    }

    /** Extracts the linked employee ID from the {@code empId} claim. Returns null if absent. */
    public Long extractEmployeeId(String token) {
        Object empId = extractAllClaims(token).get("empId");
        if (empId == null || "".equals(empId)) return null;
        return empId instanceof Integer i ? i.longValue() : (Long) empId;
    }

    /** Returns the token expiry date. */
    public Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }

    /** Returns {@code true} if the token has expired. */
    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    private String buildToken(String subject, List<String> roles,
                              long expirationMs, Map<String, Object> extraClaims) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        JwtBuilder builder = Jwts.builder()
            .subject(subject)
            .claim("roles", roles)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(getSigningKey());

        if (extraClaims != null) {
            extraClaims.forEach(builder::claim);
        }

        return builder.compact();
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private List<String> extractRoles(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
            .map(GrantedAuthority::getAuthority)
            .map(role -> role.startsWith("ROLE_") ? role.substring(5) : role)
            .collect(Collectors.toList());
    }
}
