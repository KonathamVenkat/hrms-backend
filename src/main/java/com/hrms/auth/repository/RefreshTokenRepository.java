package com.hrms.auth.repository;

import com.hrms.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
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

    /** House-keeping — purge expired or revoked tokens older than 30 days. */
    @Modifying
    @Query("""
        DELETE FROM RefreshToken rt
        WHERE (rt.isRevoked = true OR rt.expiryDate < :cutoff)
        """)
    int deleteExpiredAndRevoked(@Param("cutoff") Instant cutoff);
}
