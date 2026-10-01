package com.hrms.common.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The identifier written to {@code CREATED_BY} / {@code UPDATED_BY} style columns for the current
 * request: the authenticated user's name, or {@code SYSTEM} for background work and requests
 * without a signed-in user.
 */
public final class CurrentAuditor {

    public static final String SYSTEM_USER = "SYSTEM";

    private CurrentAuditor() {}

    public static String name() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return SYSTEM_USER;
        }
        String name = authentication.getName();
        return name != null ? name : SYSTEM_USER;
    }
}
