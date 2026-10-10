package com.aifsar.pos.reports.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.aifsar.pos.common.security.CurrentPrincipal;
import com.aifsar.pos.reports.domain.TransactionEventRecord;
import com.aifsar.pos.reports.domain.TransactionEventRecordRepository;
import com.aifsar.pos.reports.domain.TransactionView;
import com.aifsar.pos.reports.domain.TransactionViewRepository;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportService {

    public record TransactionFilter(String status, String merchantId, String terminalId, Instant from, Instant to) {
    }

    public record StatusLine(String status, long count, long amountMinor) {
    }

    public record Summary(Instant from, Instant to, String merchantId, long totalTransactions, long approvedCount,
                          long approvedAmountMinor, double approvalRatePercent, long declinedCount,
                          long reversedCount, long pendingReversalCount, long duplicatesBlocked,
                          List<StatusLine> byStatus) {
    }

    public record Timeline(TransactionView transaction, List<TransactionEventRecord> events) {
    }

    private final TransactionViewRepository views;
    private final TransactionEventRecordRepository events;

    public ReportService(TransactionViewRepository views, TransactionEventRecordRepository events) {
        this.views = views;
        this.events = events;
    }

    public Page<TransactionView> search(TransactionFilter filter, int page, int size, CurrentPrincipal caller) {
        String merchant = scopeMerchant(filter.merchantId(), caller);
        Specification<TransactionView> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (merchant != null) p.add(cb.equal(root.get("merchantId"), merchant));
            if (filter.status() != null) p.add(cb.equal(root.get("status"), filter.status()));
            if (filter.terminalId() != null) p.add(cb.equal(root.get("terminalId"), filter.terminalId()));
            if (filter.from() != null) p.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), filter.from()));
            if (filter.to() != null) p.add(cb.lessThan(root.<Instant>get("createdAt"), filter.to()));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return views.findAll(spec, PageRequest.of(page, Math.min(size, 500), Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    public Timeline timeline(UUID paymentId, CurrentPrincipal caller) {
        TransactionView view = views.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown payment " + paymentId));
        if (!caller.seesAllMerchants() && !view.getMerchantId().equals(caller.merchantId())) {
            throw new AccessDeniedException("Payment belongs to another merchant");
        }
        return new Timeline(view, events.findByPaymentIdOrderBySequenceAscReceivedAtAsc(paymentId));
    }

    public Summary summary(Instant from, Instant to, String merchantId, CurrentPrincipal caller) {
        String merchant = scopeMerchant(merchantId, caller);
        List<Object[]> rows = merchant == null ? views.summarize(from, to)
                : views.summarizeForMerchant(merchant, from, to);

        Map<String, StatusLine> byStatus = new LinkedHashMap<>();
        long total = 0;
        long duplicates = 0;
        for (Object[] r : rows) {
            String status = (String) r[0];
            long count = ((Number) r[1]).longValue();
            byStatus.put(status, new StatusLine(status, count, ((Number) r[2]).longValue()));
            total += count;
            duplicates += ((Number) r[3]).longValue();
        }
        long approved = count(byStatus, "AUTHORIZED") + count(byStatus, "CAPTURED") + count(byStatus, "SETTLED");
        long approvedAmount = amount(byStatus, "AUTHORIZED") + amount(byStatus, "CAPTURED")
                + amount(byStatus, "SETTLED");
        long decided = total - count(byStatus, "INITIATED");
        double rate = decided == 0 ? 0.0 : Math.round(approved * 10000.0 / decided) / 100.0;

        return new Summary(from, to, merchant, total, approved, approvedAmount, rate, count(byStatus, "DECLINED"),
                count(byStatus, "REVERSED"), count(byStatus, "TIMEOUT_PENDING_REVERSAL"), duplicates,
                List.copyOf(byStatus.values()));
    }

    /** Ops view: anything whose outcome is still unknown after the grace period. */
    public List<TransactionView> needingAttention(Duration olderThan, CurrentPrincipal caller) {
        return views.needingAttention(Instant.now().minus(olderThan)).stream()
                .filter(v -> caller.seesAllMerchants() || v.getMerchantId().equals(caller.merchantId()))
                .toList();
    }

    /** Merchants are pinned to their own merchantId no matter what they ask for. */
    private static String scopeMerchant(String requested, CurrentPrincipal caller) {
        if (caller.seesAllMerchants()) {
            return requested;
        }
        if (requested != null && !requested.equals(caller.merchantId())) {
            throw new AccessDeniedException("Merchants can only report on their own transactions");
        }
        return caller.merchantId();
    }

    private static long count(Map<String, StatusLine> m, String status) {
        StatusLine l = m.get(status);
        return l == null ? 0 : l.count();
    }

    private static long amount(Map<String, StatusLine> m, String status) {
        StatusLine l = m.get(status);
        return l == null ? 0 : l.amountMinor();
    }
}
