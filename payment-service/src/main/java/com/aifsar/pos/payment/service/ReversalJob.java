package com.aifsar.pos.payment.service;

import java.time.Instant;
import java.util.List;

import com.aifsar.pos.payment.config.PaymentProperties;
import com.aifsar.pos.payment.domain.Payment;
import com.aifsar.pos.payment.domain.PaymentRepository;
import com.aifsar.pos.payment.issuer.IssuerClient;
import com.aifsar.pos.payment.issuer.IssuerResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Store-and-forward reversals: keeps sending a reversal for every payment whose outcome is
 * unknown until the issuer acknowledges it. Safe to run on every replica at once
 * (FOR UPDATE SKIP LOCKED) and safe to repeat (the issuer de-duplicates by stan/rrn).
 */
@Component
@ConditionalOnProperty(prefix = "payments.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReversalJob {

    private static final Logger log = LoggerFactory.getLogger(ReversalJob.class);
    private static final int BATCH = 20;

    private final PaymentRepository payments;
    private final IssuerClient issuer;
    private final OutboxWriter outbox;
    private final TransactionTemplate tx;
    private final PaymentProperties props;

    public ReversalJob(PaymentRepository payments, IssuerClient issuer, OutboxWriter outbox, TransactionTemplate tx,
                       PaymentProperties props) {
        this.payments = payments;
        this.issuer = issuer;
        this.outbox = outbox;
        this.tx = tx;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${payments.reversal.interval-ms:5000}")
    public void run() {
        recoverStuckInitiated();
        sendPendingReversals();
    }

    /** INITIATED for too long = the pod died mid-call. Outcome unknown, so queue a reversal. */
    void recoverStuckInitiated() {
        Instant cutoff = Instant.now().minus(props.reversal().stuckInitiatedAfter());
        tx.executeWithoutResult(status -> {
            for (Payment p : payments.lockStuckInitiated(cutoff, BATCH)) {
                log.warn("Payment {} stuck in INITIATED since {}; queueing reversal", p.getId(), p.getCreatedAt());
                p.markPendingReversal("Recovered after interruption");
                payments.saveAndFlush(p);
                outbox.statusChanged(p);
            }
        });
    }

    void sendPendingReversals() {
        tx.executeWithoutResult(status -> {
            List<Payment> batch = payments.lockPendingReversals(props.reversal().maxAttempts(), BATCH);
            for (Payment p : batch) {
                IssuerResult result = issuer.reverse(p);
                if (result instanceof IssuerResult.Approved) {
                    p.reversed();
                    payments.saveAndFlush(p);
                    outbox.statusChanged(p);
                    log.info("Reversal acknowledged for payment {} (stan={}, rrn={})", p.getId(), p.getStan(),
                            p.getRrn());
                } else {
                    p.reversalAttemptFailed();
                    payments.saveAndFlush(p);
                    if (p.getReversalAttempts() >= props.reversal().maxAttempts()) {
                        log.error("Payment {} reversal gave up after {} attempts: {}. Needs reconciliation.",
                                p.getId(), p.getReversalAttempts(), result);
                    } else {
                        log.warn("Reversal attempt {} for payment {} failed: {}", p.getReversalAttempts(),
                                p.getId(), result);
                    }
                }
            }
        });
    }
}
