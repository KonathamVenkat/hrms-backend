package com.hrms.auth.security;

import com.hrms.auth.entity.AuthUser;
import com.hrms.auth.repository.AuthUserRepository;
import com.hrms.common.enums.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
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
     * True when the caller is HR_ADMIN, or is the employee identified by {@code employeeId} themself. Used in
     * {@code @PreAuthorize} by the endpoints where an employee fills in and edits their own details, so the rule is
     * written on the endpoint and cannot be forgotten: {@code @employeeAccessGuard.isSelfOrAdmin(#employeeId)}.
     */
    public boolean isSelfOrAdmin(Long employeeId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!isSignedIn(auth)) {
            return false;
        }
        return isHrAdmin() || isCaller(auth, employeeId);
    }

    /** True when the caller is HR_ADMIN or HR_MANAGER, or is the employee identified by {@code employeeId} themself. */
    public boolean isSelfOrPrivileged(Long employeeId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!isSignedIn(auth)) {
            return false;
        }
        return isPrivileged(auth) || isCaller(auth, employeeId);
    }

    /** Spring marks an anonymous caller as authenticated too; it is never a signed-in user. */
    private static boolean isSignedIn(Authentication auth) {
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }

    private boolean isCaller(Authentication auth, Long employeeId) {
        Long callerEmployeeId = currentEmployeeId(auth);
        return callerEmployeeId != null && callerEmployeeId.equals(employeeId);
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

    /**
     * True when the caller may see sensitive values (identity numbers) unmasked: HR_ADMIN, or the
     * employee themself. HR_MANAGER and everyone else get masked values.
     */
    public boolean canViewUnmasked(Long employeeId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_HR_ADMIN"))) {
            return true;
        }
        Long callerEmployeeId = currentEmployeeId(auth);
        return callerEmployeeId != null && callerEmployeeId.equals(employeeId);
    }

    /** True when the caller holds ROLE_HR_ADMIN. */
    public boolean isHrAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
            && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_HR_ADMIN"));
    }

    /** True when the caller is HR_ADMIN or HR_MANAGER. */
    public boolean isHrStaff() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && isPrivileged(auth);
    }

    /**
     * True when the employee's own active login has {@code role} and no other active login linked to an employee has
     * it: nobody but that employee could act on a step held by the role. False for an unknown role name.
     */
    public boolean isSoleActiveHolderOf(String role, long employeeId) {
        UserRole userRole;
        try {
            userRole = UserRole.valueOf(role);
        } catch (IllegalArgumentException e) {
            return false;
        }
        boolean holds = authUserRepository.findByEmployeeId(employeeId)
            .filter(u -> Boolean.TRUE.equals(u.getIsActive()) && u.getRole() == userRole).isPresent();
        return holds && authUserRepository.countOtherActiveHolders(userRole, employeeId) == 0;
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
