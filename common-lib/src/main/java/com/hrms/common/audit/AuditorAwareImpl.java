package com.hrms.common.audit;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Provides the current authenticated user's identifier to Spring Data JPA's
 * auditing infrastructure for populating {@code CREATED_BY} and {@code UPDATED_BY} fields.
 *
 * <p>The auditor value is resolved from the Spring Security context.
 * In the HRMS system, the JWT principal is the user's UUID (from the auth-service),
 * which is stored as a 36-character string (UUID format) matching the Oracle
 * {@code CREATED_BY VARCHAR2(36)} and {@code UPDATED_BY VARCHAR2(36)} columns.</p>
 *
 * <p>This bean must be active for {@code @EnableJpaAuditing} to work. Enable it
 * in each microservice's main application class:
 * <pre>{@code
 * @EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")
 * }</pre>
 * </p>
 */
@Component("auditorAwareImpl")
public class AuditorAwareImpl implements AuditorAware<String> {

    private static final String SYSTEM_USER = "SYSTEM";

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            // Fallback for system operations (e.g., scheduled tasks, data migrations)
            return Optional.of(SYSTEM_USER);
        }

        // The JWT filter populates the principal with the user's UUID string
        return Optional.ofNullable(authentication.getName());
    }
}
