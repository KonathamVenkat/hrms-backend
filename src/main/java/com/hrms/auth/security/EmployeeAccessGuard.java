package com.hrms.auth.security;

import com.hrms.auth.entity.AuthUser;
import com.hrms.auth.repository.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Guards "employee self-service" endpoints shaped like
 * {@code /api/v1/employees/{employeeId}/...} — HR_ADMIN/HR_MANAGER may act on
 * any employeeId, but a plain EMPLOYEE may only act on their own. Without this,
 * any authenticated EMPLOYEE could read/modify another employee's records by
 * editing the path's employeeId, since {@code @PreAuthorize} alone only checks
 * role membership, never which employeeId the caller actually is.
 *
 * <p>The JWT does carry an {@code employeeId} claim at login, but
 * {@code JwtAuthenticationFilter} (common-lib) only puts username + roles into
 * the security context, so the caller's own employeeId is resolved here via a
 * lookup by username instead of touching the shared filter.</p>
 */
@Component
@RequiredArgsConstructor
public class EmployeeAccessGuard {

    private final AuthUserRepository authUserRepository;

    /**
     * Throws {@link AccessDeniedException} unless the caller is HR_ADMIN/HR_MANAGER
     * or is the employee identified by {@code employeeId} themself.
     */
    public void assertSelfOrPrivileged(Long employeeId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required.");
        }
        if (isPrivileged(auth)) {
            return;
        }
        Long callerEmployeeId = currentEmployeeId(auth);
        if (callerEmployeeId == null || !callerEmployeeId.equals(employeeId)) {
            throw new AccessDeniedException("You can only access your own records.");
        }
    }

    /**
     * The caller's own employeeId (resolved server-side from the authenticated user), or
     * null if the account isn't linked to an employee record. Use this instead of trusting
     * an employee/reviewer id sent by the client.
     */
    public Long currentEmployeeId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required.");
        }
        return currentEmployeeId(auth);
    }

    private boolean isPrivileged(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a ->
            a.getAuthority().equals("ROLE_HR_ADMIN") || a.getAuthority().equals("ROLE_HR_MANAGER"));
    }

    private Long currentEmployeeId(Authentication auth) {
        return authUserRepository.findByUsernameOrEmail(auth.getName())
            .map(AuthUser::getEmployeeId)
            .orElse(null);
    }
}
