package com.aifsar.pos.payment.service;

import com.aifsar.pos.payment.domain.Payment;
import com.aifsar.pos.payment.domain.PaymentRepository;
import com.aifsar.pos.payment.domain.PaymentStatus;
import com.aifsar.pos.payment.issuer.IssuerClient;
import com.aifsar.pos.payment.issuer.IssuerResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * End-of-day batch close: captures every still-AUTHORIZED payment, then marks CAPTURED ones
 * SETTLED (standing in for the network's settlement file). Re-running it is harmless.
 */
@Service
public class BatchCloseService {

    private static final Logger log = LoggerFactory.getLogger(BatchCloseService.class);

    public record BatchResult(int captured, int settled, int failed) {
    }

    private final PaymentRepository payments;
    private final IssuerClient issuer;
    private final OutboxWriter outbox;
    private final TransactionTemplate tx;

    public BatchCloseService(PaymentRepository payments, IssuerClient issuer, OutboxWriter outbox,
                             TransactionTemplate tx) {
        this.payments = payments;
        this.issuer = issuer;
        this.outbox = outbox;
        this.tx = tx;
    }

    public BatchResult close(String merchantId) {
        int[] counts = new int[3];
        tx.executeWithoutResult(status -> {
            for (Payment p : payments.lockOpenForBatch(merchantId, 500)) {
                if (p.getStatus() == PaymentStatus.AUTHORIZED) {
                    if (issuer.capture(p, p.getAmountMinor()) instanceof IssuerResult.Approved) {
                        p.captured(p.getAmountMinor());
                        counts[0]++;
                    } else {
                        counts[2]++;
                        continue; // stays AUTHORIZED, picked up by the next batch
                    }
                }
                p.settled();
                counts[1]++;
                payments.saveAndFlush(p);
                outbox.statusChanged(p);
            }
        });
        log.info("Batch close merchant={} captured={} settled={} failed={}", merchantId, counts[0], counts[1],
                counts[2]);
        return new BatchResult(counts[0], counts[1], counts[2]);
    }
}
