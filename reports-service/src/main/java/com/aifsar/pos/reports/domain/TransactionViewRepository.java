package com.aifsar.pos.reports.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionViewRepository
        extends JpaRepository<TransactionView, UUID>, JpaSpecificationExecutor<TransactionView> {

    /** Row per status: [status, count, sum(amountMinor), sum(duplicateAttempts)]. */
    @Query("select v.status, count(v), coalesce(sum(v.amountMinor), 0), coalesce(sum(v.duplicateAttempts), 0) "
            + "from TransactionView v where v.createdAt >= :from and v.createdAt < :to group by v.status")
    List<Object[]> summarize(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select v.status, count(v), coalesce(sum(v.amountMinor), 0), coalesce(sum(v.duplicateAttempts), 0) "
            + "from TransactionView v where v.merchantId = :merchantId "
            + "and v.createdAt >= :from and v.createdAt < :to group by v.status")
    List<Object[]> summarizeForMerchant(@Param("merchantId") String merchantId, @Param("from") Instant from,
                                        @Param("to") Instant to);

    /** Payments whose outcome is still unknown after the cutoff: an operations alert list. */
    @Query("select v from TransactionView v where v.status in ('INITIATED', 'TIMEOUT_PENDING_REVERSAL') "
            + "and v.updatedAt < :cutoff order by v.updatedAt")
    List<TransactionView> needingAttention(@Param("cutoff") Instant cutoff);
}
