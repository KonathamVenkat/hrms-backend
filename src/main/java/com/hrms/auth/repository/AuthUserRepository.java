package com.hrms.auth.repository;

import com.hrms.auth.entity.AuthUser;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface AuthUserRepository extends JpaRepository<AuthUser, Long> {

    /**
     * Primary lookup for login — tries username OR email (case-insensitive).
     */
    @Query("""
        SELECT u FROM AuthUser u
        WHERE (LOWER(u.username) = LOWER(:identifier)
           OR  LOWER(u.email)    = LOWER(:identifier))
          AND u.isActive = true
        """)
    Optional<AuthUser> findByUsernameOrEmail(@Param("identifier") String identifier);

    /**
     * Same lookup, but the row stays locked until the transaction ends. Login and change-password use it so that
     * concurrent password guesses for one account queue up and each sees the previous attempt's committed count.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT u FROM AuthUser u
        WHERE (LOWER(u.username) = LOWER(:identifier)
           OR  LOWER(u.email)    = LOWER(:identifier))
          AND u.isActive = true
        """)
    Optional<AuthUser> findByUsernameOrEmailForUpdate(@Param("identifier") String identifier);
    
    Optional<AuthUser> findByEmployeeId(Long employeeId);
    
    boolean existsByUsername(String username);       // ← ADD

    boolean existsByEmail(String email);     

    Optional<AuthUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Increment failed attempts and auto-lock after threshold — done in-DB
     * to avoid a read-modify-write race under concurrent logins.
     */
    @Modifying
    @Query("""
        UPDATE AuthUser u
        SET u.failedAttempts = u.failedAttempts + 1,
            u.isLocked       = CASE WHEN u.failedAttempts + 1 >= 5 THEN true ELSE false END,
            u.lockTime       = CASE WHEN u.failedAttempts + 1 >= 5 THEN :now ELSE u.lockTime END
        WHERE u.userId = :userId
        """)
    void incrementFailedAttempts(@Param("userId") Long userId,
                                  @Param("now") LocalDateTime now);

    @Modifying
    @Query("""
        UPDATE AuthUser u
        SET u.failedAttempts = 0,
            u.isLocked       = false,
            u.lockTime       = null,
            u.lastLogin      = :now
        WHERE u.userId = :userId
        """)
    void resetFailedAttemptsAndUpdateLastLogin(@Param("userId") Long userId,
                                               @Param("now") LocalDateTime now);
}
