package com.hrms.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Persisted refresh tokens — allows server-side revocation (logout, security events).
 * Maps to HRMS.AUTH_REFRESH_TOKENS.
 */
@Entity
@Table(
    name   = "AUTH_REFRESH_TOKENS",
    schema = "HRMS",
    indexes = {
        @Index(name = "IDX_RT_USER_ID", columnList = "USER_ID"),
        @Index(name = "IDX_RT_TOKEN",   columnList = "TOKEN", unique = true)
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rt_seq")
    @SequenceGenerator(name = "rt_seq", sequenceName = "HRMS.SEQ_AUTH_RT", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    /** The opaque refresh token value (UUID or secure random). */
    @Column(name = "TOKEN", nullable = false, length = 255, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false, foreignKey = @ForeignKey(name = "FK_RT_USER"))
    private AuthUser user;

    @Column(name = "EXPIRY_DATE", nullable = false)
    private Instant expiryDate;

    @Column(name = "IS_REVOKED", nullable = false)
    @Builder.Default
    private Boolean isRevoked = false;

    /** Device/browser identifier — helps invalidate all tokens for one device. */
    @Column(name = "USER_AGENT", length = 500)
    private String userAgent;

    @Column(name = "IP_ADDRESS", length = 50)
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    // ── Helpers ──────────────────────────────────────────────────────────────

    public boolean isExpired() {
        return Instant.now().isAfter(expiryDate);
    }

    public boolean isValid() {
        return !Boolean.TRUE.equals(isRevoked) && !isExpired();
    }
}
