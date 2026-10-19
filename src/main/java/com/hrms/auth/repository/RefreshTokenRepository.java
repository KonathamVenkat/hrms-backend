package com.hrms.auth.repository;

import com.hrms.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    /** Revoke all active tokens for a user — used on logout / password change. */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true WHERE rt.user.userId = :userId")
    int revokeAllByUserId(@Param("userId") Long userId);

    /** Revoke a single token by its value. */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true WHERE rt.token = :token")
    int revokeByToken(@Param("token") String token);

    /**
     * Revokes a token only while it is still active. The count is 1 for exactly one caller, so two
     * concurrent refreshes with the same cookie cannot both rotate it.
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true WHERE rt.token = :token AND rt.isRevoked = false")
    int revokeIfActive(@Param("token") String token);

    /** True when the user was issued a token after the given time, i.e. a rotation happened moments ago. */
    boolean existsByUser_UserIdAndCreatedAtAfter(Long userId, LocalDateTime createdAfter);

    /**
     * House-keeping: purge tokens past their expiry. Revoked tokens stay until they expire, so a replay of a
     * rotated token can still be recognised.
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiryDate < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
