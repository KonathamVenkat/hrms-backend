package com.hrms.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.hrms.common.enums.UserRole;

import java.time.LocalDateTime;

/**
 * Maps to HRMS.AUTH_USERS — the central credentials table.
 * Linked to EMPLOYEES via employee_id (nullable for system/admin accounts).
 */
@Entity
@Table(
    name       = "AUTH_USERS",
    schema     = "HRMS",
    uniqueConstraints = {
        @UniqueConstraint(name = "UQ_AUTH_USERS_USERNAME", columnNames = "USERNAME"),
        @UniqueConstraint(name = "UQ_AUTH_USERS_EMAIL",    columnNames = "EMAIL")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthUser {

    // ── Primary Key ──────────────────────────────────────────────────────────
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "auth_user_seq")
    @SequenceGenerator(
        name           = "auth_user_seq",
        sequenceName   = "HRMS.AUTH_USER_SEQ",
        allocationSize = 1
    )
    @Column(name = "USER_ID")
    private Long userId;

    // ── Login identity ───────────────────────────────────────────────────────
    @Column(name = "USERNAME", nullable = false, length = 50)
    private String username;

    @Column(name = "EMAIL", nullable = false, length = 100)
    private String email;

    /**
     * BCrypt-hashed password. Never expose in responses.
     */
    @Column(name = "PASSWORD_HASH", nullable = false, length = 255)
    private String passwordHash;

    // ── Role ─────────────────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE", nullable = false, length = 20)
    private UserRole role;

    // ── Employee linkage (nullable — system users have no employee record) ───
    @Column(name = "EMPLOYEE_ID")
    private Long employeeId;

    @Column(name = "EMPLOYEE_CODE", length = 20)
    private String employeeCode;

    @Column(name = "FULL_NAME_EN", nullable = false, length = 100)
    private String fullNameEn;

    @Column(name = "DEPARTMENT_ID", length = 100)
    private String department;

    @Column(name = "AVATAR_URL", length = 500)
    private String avatarUrl;

    // ── Account state ────────────────────────────────────────────────────────
    @Column(name = "IS_ACTIVE", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "IS_LOCKED", nullable = false)
    @Builder.Default
    private Boolean isLocked = false;

    /** Consecutive failed login attempts — reset on successful login. */
    @Column(name = "FAILED_ATTEMPTS", nullable = false)
    @Builder.Default
    private Integer failedAttempts = 0;

    @Column(name = "LOCK_TIME")
    private LocalDateTime lockTime;

    @Column(name = "LAST_LOGIN")
    private LocalDateTime lastLogin;

    // ── Audit ─────────────────────────────────────────────────────────────────
    @CreationTimestamp
    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "CREATED_BY", length = 50, updatable = false)
    private String createdBy;

    @Column(name = "UPDATED_BY", length = 50)
    private String updatedBy;

    // ── Business helpers ─────────────────────────────────────────────────────

    public boolean isAccountNonLocked() {
        if (!Boolean.TRUE.equals(isLocked)) return true;
        // Auto-unlock after 30 minutes
        if (lockTime != null && lockTime.plusMinutes(30).isBefore(LocalDateTime.now())) {
            isLocked = false;
            failedAttempts = 0;
            lockTime = null;
            return true;
        }
        return false;
    }

    public void incrementFailedAttempts() {
        this.failedAttempts = (this.failedAttempts == null ? 0 : this.failedAttempts) + 1;
        if (this.failedAttempts >= 5) {
            this.isLocked = true;
            this.lockTime = LocalDateTime.now();
        }
    }

    public void resetFailedAttempts() {
        this.failedAttempts = 0;
        this.isLocked = false;
        this.lockTime = null;
        this.lastLogin = LocalDateTime.now();
    }
}
