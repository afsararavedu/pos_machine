package com.aifsar.pos.payment.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /*
     * Background jobs use FOR UPDATE SKIP LOCKED so several replicas can run the same job
     * without processing the same row twice and without blocking each other.
     */

    @Query(value = "SELECT * FROM payments WHERE status = 'TIMEOUT_PENDING_REVERSAL' "
            + "AND reversal_attempts < :maxAttempts ORDER BY updated_at LIMIT :limit FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<Payment> lockPendingReversals(@Param("maxAttempts") int maxAttempts, @Param("limit") int limit);

    @Query(value = "SELECT * FROM payments WHERE status = 'INITIATED' AND created_at < :cutoff "
            + "ORDER BY created_at LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<Payment> lockStuckInitiated(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    @Query(value = "SELECT * FROM payments WHERE status IN ('AUTHORIZED', 'CAPTURED') "
            + "AND (CAST(:merchantId AS VARCHAR) IS NULL OR merchant_id = :merchantId) "
            + "ORDER BY created_at LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<Payment> lockOpenForBatch(@Param("merchantId") String merchantId, @Param("limit") int limit);
}
