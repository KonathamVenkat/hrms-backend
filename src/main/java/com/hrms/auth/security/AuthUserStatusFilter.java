package com.hrms.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.auth.entity.AuthUser;
import com.hrms.auth.repository.AuthUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Re-checks the signed-in user against the database on every authenticated request.
 *
 * <p>The JWT alone can't express "this account was deactivated", "this user's role
 * changed" or "this user still has to change a temporary password" — it stays valid for
 * its whole lifetime. This filter closes those gaps server-side:</p>
 * <ul>
 *   <li>deactivated / unknown account → 401 (session ends immediately)</li>
 *   <li>role in the token no longer matches the account → 401 (sign in again)</li>
 *   <li>must-change-password flag set → 403 {@code PASSWORD_CHANGE_REQUIRED} for everything
 *       except changing the password, refreshing, logging out and reading own profile</li>
 * </ul>
 *
 * <p>Registered by {@code SecurityConfig} directly (not a Spring bean) so it runs once, in
 * the security chain, after the JWT filter has populated the security context.</p>
 */
public class AuthUserStatusFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_WHILE_PASSWORD_CHANGE_REQUIRED = Set.of(
            "/api/v1/auth/change-password",
            "/api/v1/auth/logout",
            "/api/v1/auth/refresh",
            "/api/v1/auth/me");

    private final AuthUserRepository authUserRepository;
    private final ObjectMapper       objectMapper;

    public AuthUserStatusFilter(AuthUserRepository authUserRepository, ObjectMapper objectMapper) {
        this.authUserRepository = authUserRepository;
        this.objectMapper       = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            chain.doFilter(request, response);
            return;
        }

        Optional<AuthUser> found = authUserRepository.findByUsernameOrEmail(auth.getName());
        if (found.isEmpty()) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, null,
                    "Your account is no longer active. Please contact HR.");
            return;
        }
        AuthUser user = found.get();

        String expectedAuthority = "ROLE_" + user.getRole().name();
        boolean roleMatches = auth.getAuthorities().stream()
                .anyMatch(a -> expectedAuthority.equals(a.getAuthority()));
        if (!roleMatches) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, null,
                    "Your access has changed. Please sign in again.");
            return;
        }

        if (user.getMustChangePassword() != null && user.getMustChangePassword() == 1
                && !ALLOWED_WHILE_PASSWORD_CHANGE_REQUIRED.contains(request.getServletPath())) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, "PASSWORD_CHANGE_REQUIRED",
                    "You must change your password before continuing.");
            return;
        }

        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = code == null
                ? Map.of("success", false, "message", message, "statusCode", status)
                : Map.of("success", false, "message", message, "statusCode", status, "code", code);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
