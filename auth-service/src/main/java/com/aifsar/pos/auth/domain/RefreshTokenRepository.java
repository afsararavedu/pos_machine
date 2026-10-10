package com.aifsar.pos.auth.domain;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Row lock so two concurrent refreshes with the same token cannot both succeed:
     * the second waits, then sees the token already rotated and is treated as reuse.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefreshToken r where r.tokenHash = :hash")
    Optional<RefreshToken> findForUpdate(@Param("hash") String tokenHash);

    @Modifying
    @Query("update RefreshToken r set r.revoked = true, r.revokedReason = :reason "
            + "where r.familyId = :familyId and r.revoked = false")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("reason") String reason);

    @Modifying
    @Query("update RefreshToken r set r.revoked = true, r.revokedReason = :reason "
            + "where r.userId = :userId and r.revoked = false")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("reason") String reason);
}
