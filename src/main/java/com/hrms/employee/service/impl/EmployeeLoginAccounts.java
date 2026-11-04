package com.hrms.employee.service.impl;

import com.hrms.auth.entity.AuthUser;
import com.hrms.auth.repository.AuthUserRepository;
import com.hrms.auth.repository.RefreshTokenRepository;
import com.hrms.common.audit.CurrentAuditor;
import com.hrms.common.enums.UserRole;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.employee.dto.request.CreateEmployeeRequest;
import com.hrms.employee.entity.Employee;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * The login account (AUTH_USERS row) that goes with an employee: creating it, keeping its role and
 * name in step, and switching it off and on with the employee. Runs inside the calling service's
 * transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmployeeLoginAccounts {

    private final AuthUserRepository authUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;

    public boolean usernameTaken(String username) {
        return authUserRepository.existsByUsernameIgnoreCase(username);
    }

    public boolean emailTaken(String email) {
        return authUserRepository.existsByEmailIgnoreCase(email);
    }

    public Optional<UserRole> roleOf(Long employeeId) {
        return authUserRepository.findByEmployeeId(employeeId).map(AuthUser::getRole);
    }

    /** Creates the login for a newly saved employee. HR chose the password, so it must be changed at first sign-in. */
    public void create(Employee employee, CreateEmployeeRequest request, UserRole role, String workEmail) {
        String fullNameEn = buildFullName(
            request.getFirstName().trim(),
            request.getMiddleName(),
            request.getLastName().trim());

        AuthUser authUser = AuthUser.builder()
            .username(request.getUsername().trim().toLowerCase())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .email(workEmail)
            .fullNameEn(fullNameEn)
            .role(role)
            .employeeId(employee.getId())
            .employeeCode(employee.getEmployeeCode())
            .failedAttempts(0)
            .mustChangePassword(1)
            .isActive(true)
            .isLocked(false)
            .createdBy(CurrentAuditor.name())
            .createdAt(LocalDateTime.now())
            .build();

        authUserRepository.save(authUser);
        log.info("AuthUser created for employee ID: {}", employee.getId());
    }

    /** An HR_ADMIN must not strip their own admin rights; another HR_ADMIN has to do it. */
    public void requireNotDemotingSelf(Long employeeId) {
        authUserRepository.findByEmployeeId(employeeId).ifPresent(au -> {
            if (au.getRole() == UserRole.HR_ADMIN) {
                throw new BusinessRuleException(
                    "CANNOT_DEMOTE_SELF",
                    "You cannot remove your own HR_ADMIN role. Ask another HR_ADMIN to do it.");
            }
        });
    }

    /** @return true when the role actually changed */
    public boolean changeRole(Employee employee, String roleStr) {
        UserRole newRole;
        try {
            newRole = UserRole.valueOf(roleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException(
                "INVALID_ROLE",
                "Invalid role: '" + roleStr + "'. Allowed values: HR_ADMIN, HR_MANAGER, EMPLOYEE");
        }
        return authUserRepository.findByEmployeeId(employee.getId())
            .map(authUser -> {
                if (authUser.getRole().equals(newRole)) {
                    return false;
                }
                authUser.setRole(newRole);
                authUser.setUpdatedAt(LocalDateTime.now());
                authUserRepository.save(authUser);
                log.info("Updated auth user role to {} for employee {}", newRole, employee.getId());
                return true;
            })
            .orElse(false);
    }

    public void syncFullName(Employee employee) {
        String fullNameEn = buildFullName(
            employee.getFirstName(),
            employee.getMiddleName(),
            employee.getLastName());

        authUserRepository.findByEmployeeId(employee.getId())
            .ifPresent(authUser -> {
                authUser.setFullNameEn(fullNameEn);
                authUser.setUpdatedAt(LocalDateTime.now());
                authUserRepository.save(authUser);
            });
    }

    /**
     * Revokes or restores the login together with the employee record. Every session issued before the change is
     * ended (refresh tokens revoked), so leaving cuts off all access at once and a re-hire starts clean: the person
     * must set a new password at the first sign-in, and an old lock or failed-attempt count does not come back.
     */
    public void setActive(Long employeeId, boolean active, String updatedBy) {
        authUserRepository.findByEmployeeId(employeeId).ifPresent(authUser -> {
            authUser.setIsActive(active);
            if (active) {
                authUser.setMustChangePassword(1);
                authUser.setFailedAttempts(0);
                authUser.setIsLocked(false);
                authUser.setLockTime(null);
            }
            authUser.setUpdatedBy(updatedBy);
            authUser.setUpdatedAt(LocalDateTime.now());
            authUserRepository.save(authUser);
            refreshTokenRepository.revokeAllByUserId(authUser.getUserId());
            log.info("AuthUser for employee ID: {} {} alongside employee",
                employeeId, active ? "reactivated" : "deactivated");
        });
    }

    /**
     * Refuses to remove the last active HR_ADMIN. The active admin logins are locked while this runs, so two admins
     * deactivating each other at the same moment cannot both pass the check.
     */
    public void requireAnotherActiveAdmin(Long employeeId) {
        if (roleOf(employeeId).filter(r -> r == UserRole.HR_ADMIN).isEmpty()) {
            return;
        }
        List<AuthUser> admins = authUserRepository.findActiveByRoleForUpdate(UserRole.HR_ADMIN);
        boolean targetIsActiveAdmin = admins.stream().anyMatch(a -> employeeId.equals(a.getEmployeeId()));
        boolean anotherAdmin = admins.stream().anyMatch(a -> !employeeId.equals(a.getEmployeeId()));
        if (targetIsActiveAdmin && !anotherAdmin) {
            throw new BusinessRuleException("LAST_HR_ADMIN",
                "This is the last active HR_ADMIN. Make another employee an HR_ADMIN first.");
        }
    }

    private static String buildFullName(String first, String middle, String last) {
        StringBuilder sb = new StringBuilder();
        if (first  != null && !first.isBlank())  sb.append(first.trim());
        if (middle != null && !middle.isBlank()) sb.append(" ").append(middle.trim());
        if (last   != null && !last.isBlank())   sb.append(" ").append(last.trim());
        return sb.toString().trim();
    }
}
