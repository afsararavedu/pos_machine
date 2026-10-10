package com.aifsar.pos.reports.service;

import com.aifsar.pos.common.event.TransactionEvent;
import com.aifsar.pos.reports.domain.TransactionEventRecord;
import com.aifsar.pos.reports.domain.TransactionEventRecordRepository;
import com.aifsar.pos.reports.domain.TransactionView;
import com.aifsar.pos.reports.domain.TransactionViewRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent consumer. Two independent guards make re-delivery harmless:
 *  1. eventId already recorded  -> skip entirely (Kafka redelivery, outbox resend).
 *  2. sequence not newer        -> keep audit row, but do not move the projection backwards
 *                                  (out-of-order delivery after a rebalance or multi-relay publish).
 * Both writes happen in one DB transaction, so a crash can never record an event as
 * processed without also applying it.
 */
@Service
public class TransactionEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventProcessor.class);

    public enum Result { APPLIED, DUPLICATE, STALE }

    private final TransactionEventRecordRepository events;
    private final TransactionViewRepository views;

    public TransactionEventProcessor(TransactionEventRecordRepository events, TransactionViewRepository views) {
        this.events = events;
        this.views = views;
    }

    @Transactional
    public Result apply(TransactionEvent e) {
        if (e.eventId() == null || e.paymentId() == null) {
            throw new IllegalArgumentException("Event without eventId/paymentId");
        }
        if (events.existsById(e.eventId())) {
            log.debug("Event {} already processed; skipping", e.eventId());
            return Result.DUPLICATE;
        }
        events.save(TransactionEventRecord.from(e));

        TransactionView view = views.findById(e.paymentId()).orElse(null);
        if (TransactionEvent.DUPLICATE_BLOCKED.equals(e.eventType())) {
            if (view == null) {
                view = views.save(TransactionView.from(e));
            }
            view.duplicateBlocked();
            return Result.APPLIED;
        }
        if (view == null) {
            views.save(TransactionView.from(e));
            return Result.APPLIED;
        }
        if (!view.apply(e)) {
            log.info("Stale event {} (seq {} <= {}) for payment {} ignored", e.eventId(), e.sequence(),
                    view.getLastSequence(), e.paymentId());
            return Result.STALE;
        }
        return Result.APPLIED;
    }
}
