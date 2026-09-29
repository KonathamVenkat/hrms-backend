package com.hrms.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT authentication filter — runs once per request in every HRMS microservice.
 *
 * <p>Intercepts incoming HTTP requests, extracts the Bearer token from the
 * {@code Authorization} header, validates it via {@link JwtTokenProvider},
 * and populates the Spring Security context with the authenticated principal.</p>
 *
 * <h3>Token flow:</h3>
 * <pre>
 *   Angular  →  "Authorization: Bearer eyJ..."
 *     ↓
 *   JwtAuthenticationFilter.doFilterInternal()
 *     ↓ validates token
 *   SecurityContextHolder ← UsernamePasswordAuthenticationToken(uuid, roles)
 *     ↓
 *   Controller @PreAuthorize checks pass
 * </pre>
 *
 * <h3>Principal:</h3>
 * The authenticated principal name is the user's UUID (matching Oracle CREATED_BY/UPDATED_BY),
 * and authorities are the roles extracted from the JWT {@code roles} claim,
 * prefixed with {@code ROLE_} for Spring Security compatibility.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX         = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String token = extractBearerToken(request);

            if (token != null && jwtTokenProvider.validateToken(token)) {

                // Reject refresh tokens from being used as access tokens
                if (jwtTokenProvider.isRefreshToken(token)) {
                    log.warn("Refresh token presented as access token from IP: {}",
                        request.getRemoteAddr());
                    filterChain.doFilter(request, response);
                    return;
                }

                // Extract claims
                String       userUuid = jwtTokenProvider.extractSubject(token);
                List<String> roles    = jwtTokenProvider.extractRoles(token);

                // Build authorities with ROLE_ prefix
                List<SimpleGrantedAuthority> authorities = roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(Collectors.toList());

                // Build authentication token
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userUuid, null, authorities);
                authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // Set in Security Context — available to @PreAuthorize, AuditorAware, etc.
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Authenticated user: {} with roles: {} for URI: {}",
                    userUuid, roles, request.getRequestURI());
            }

        } catch (Exception ex) {
            log.error("Failed to set user authentication in security context: {}", ex.getMessage());
            // Do not throw — let the request continue and let Spring Security
            // return 401 for protected endpoints when context is not populated
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the raw JWT string from the Authorization header.
     * Returns {@code null} if the header is absent or not a Bearer token.
     */
    private String extractBearerToken(HttpServletRequest request) {
        String headerValue = request.getHeader(AUTHORIZATION_HEADER);
        // Never log the header: it carries the bearer token, and com.hrms logs at DEBUG.
        if (StringUtils.hasText(headerValue) && headerValue.startsWith(BEARER_PREFIX)) {
            return headerValue.substring(BEARER_PREFIX.length()).trim();
            //System.out.println(headerValue.substring(BEARER_PREFIX.length()).trim());
        }
        return null;
    }

    /**
     * Skip JWT validation for endpoints that are publicly accessible.
     * Actuator health checks and OpenAPI docs don't require authentication.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/actuator/health")
            || path.startsWith("/v3/api-docs")
            || path.startsWith("/swagger-ui");
    }
}
