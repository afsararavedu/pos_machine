package com.aifsar.pos.payment.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

import com.aifsar.pos.common.event.TransactionEvent;
import com.aifsar.pos.common.security.CurrentPrincipal;
import com.aifsar.pos.payment.domain.InvalidTransitionException;
import com.aifsar.pos.payment.domain.Payment;
import com.aifsar.pos.payment.domain.PaymentRepository;
import com.aifsar.pos.payment.domain.PaymentStatus;
import com.aifsar.pos.payment.issuer.IssuerClient;
import com.aifsar.pos.payment.issuer.IssuerResult;
import com.aifsar.pos.payment.service.PaymentExceptions.IdempotencyConflictException;
import com.aifsar.pos.payment.service.PaymentExceptions.IssuerRejectedException;
import com.aifsar.pos.payment.service.PaymentExceptions.IssuerRetryableException;
import com.aifsar.pos.payment.service.PaymentExceptions.PaymentNotFoundException;
import com.aifsar.pos.payment.service.PaymentExceptions.RequestInProgressException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orchestrates one card-present payment. The three-step shape is deliberate:
 *
 *  1. CLAIM    (short DB tx)  insert payment row with a UNIQUE idempotency key. Exactly one
 *                             caller wins; every concurrent duplicate fails here and replays.
 *  2. AUTHORIZE (no DB tx)    call the issuer. Never hold a DB transaction/lock across the network.
 *  3. COMPLETE (short DB tx)  move the state machine and write the outbox event atomically.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    public record PaymentOutcome(Payment payment, boolean replayed) {
    }

    private final PaymentRepository payments;
    private final OutboxWriter outbox;
    private final IssuerClient issuer;
    private final CardDataProtector cardData;
    private final TransactionTemplate tx;

    public PaymentService(PaymentRepository payments, OutboxWriter outbox, IssuerClient issuer,
                          CardDataProtector cardData, TransactionTemplate tx) {
        this.payments = payments;
        this.outbox = outbox;
        this.issuer = issuer;
        this.cardData = cardData;
        this.tx = tx;
    }

    public PaymentOutcome authorize(AuthorizeCommand cmd, String idempotencyKey) {
        if (!CardDataProtector.luhnValid(cmd.cardNumber())) {
            throw new IllegalArgumentException("Card number failed Luhn check");
        }
        String panToken = cardData.tokenize(cmd.cardNumber());
        String requestHash = sha256(String.join("|", cmd.merchantId(), cmd.terminalId(), cmd.stan(),
                Long.toString(cmd.amountMinor()), cmd.currency(), panToken, cmd.entryMode().name()));

        // ---- 1. CLAIM -------------------------------------------------------------------
        Payment claimed;
        try {
            claimed = tx.execute(status -> {
                Payment p = payments.saveAndFlush(Payment.initiate(idempotencyKey, requestHash, cmd.merchantId(),
                        cmd.terminalId(), cmd.stan(), newRrn(), panToken, CardDataProtector.mask(cmd.cardNumber()),
                        CardDataProtector.brand(cmd.cardNumber()), cmd.entryMode(), cmd.amountMinor(),
                        cmd.currency()));
                outbox.statusChanged(p);
                return p;
            });
        } catch (DataIntegrityViolationException | ConcurrencyFailureException duplicate) {
            // Another request/thread/pod already owns this key (Postgres reports a unique violation;
            // some databases report a lock/concurrency failure for the same race).
            return replay(idempotencyKey, requestHash, duplicate);
        }
        log.info("Claimed payment {} for {}", claimed.getId(), cmd);

        // ---- 2. AUTHORIZE (outside any transaction) -----------------------------------
        IssuerResult result = issuer.authorize(claimed, cmd.cardNumber());

        // ---- 3. COMPLETE ----------------------------------------------------------------
        Payment completed = tx.execute(status -> {
            Payment p = payments.findById(claimed.getId()).orElseThrow();
            switch (result) {
                case IssuerResult.Approved a -> p.authorize(a.authCode());
                case IssuerResult.Declined d -> p.decline(d.code(), d.message());
                case IssuerResult.TimedOut t -> p.markPendingReversal(t.reason());
                case IssuerResult.Unavailable u -> p.decline("91", u.reason());
            }
            payments.saveAndFlush(p);
            outbox.statusChanged(p);
            return p;
        });
        log.info("Payment {} -> {} ({})", completed.getId(), completed.getStatus(), completed.getResponseCode());
        return new PaymentOutcome(completed, false);
    }

    private PaymentOutcome replay(String key, String requestHash, RuntimeException cause) {
        Payment existing = findCommitted(key);
        if (existing == null) {
            throw cause; // not an idempotency race after all; surface the real error
        }
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(key);
        }
        if (existing.getStatus() == PaymentStatus.INITIATED) {
            throw new RequestInProgressException(existing.getId());
        }
        tx.executeWithoutResult(status -> outbox.record(existing, TransactionEvent.DUPLICATE_BLOCKED));
        log.info("Duplicate request for key {} blocked; replaying payment {} ({})", key, existing.getId(),
                existing.getStatus());
        return new PaymentOutcome(existing, true);
    }

    /** The winning insert may be a few milliseconds from committing; give it a moment to become visible. */
    private Payment findCommitted(String key) {
        for (int attempt = 0; attempt < 10; attempt++) {
            Payment p = payments.findByIdempotencyKey(key).orElse(null);
            if (p != null) {
                return p;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    public Payment get(UUID id, CurrentPrincipal caller) {
        Payment p = payments.findById(id).orElseThrow(() -> new PaymentNotFoundException(id));
        checkOwnership(p, caller);
        return p;
    }

    /** Idempotent: capturing an already-captured payment returns it unchanged. */
    public Payment capture(UUID id, Long amountMinor, CurrentPrincipal caller) {
        Payment p = get(id, caller);
        if (p.getStatus() == PaymentStatus.CAPTURED || p.getStatus() == PaymentStatus.SETTLED) {
            return p;
        }
        long amount = amountMinor == null ? p.getAmountMinor() : amountMinor;
        if (amount <= 0 || amount > p.getAmountMinor()) {
            throw new IllegalArgumentException("Capture amount must be between 1 and " + p.getAmountMinor());
        }
        if (p.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new InvalidTransitionException(id, p.getStatus(),
                    PaymentStatus.CAPTURED);
        }
        handleReferenceResult(issuer.capture(p, amount), "capture");
        return tx.execute(status -> {
            Payment fresh = payments.findById(id).orElseThrow();
            if (fresh.getStatus() == PaymentStatus.CAPTURED) {
                return fresh; // a concurrent capture won the race; issuer side was idempotent too
            }
            fresh.captured(amount);
            payments.saveAndFlush(fresh);
            outbox.statusChanged(fresh);
            return fresh;
        });
    }

    /** Merchant cancels an authorized (not yet captured) payment: release the hold at the issuer. */
    public Payment voidPayment(UUID id, CurrentPrincipal caller) {
        Payment p = get(id, caller);
        if (p.getStatus() == PaymentStatus.VOIDED) {
            return p;
        }
        if (p.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new InvalidTransitionException(id, p.getStatus(),
                    PaymentStatus.VOIDED);
        }
        handleReferenceResult(issuer.reverse(p), "void");
        return tx.execute(status -> {
            Payment fresh = payments.findById(id).orElseThrow();
            if (fresh.getStatus() == PaymentStatus.VOIDED) {
                return fresh;
            }
            fresh.voided();
            payments.saveAndFlush(fresh);
            outbox.statusChanged(fresh);
            return fresh;
        });
    }

    private static void handleReferenceResult(IssuerResult result, String action) {
        switch (result) {
            case IssuerResult.Approved a -> { }
            case IssuerResult.Declined d -> throw new IssuerRejectedException(
                    "Issuer rejected " + action + ": " + d.code() + " " + d.message());
            case IssuerResult.TimedOut t -> throw new IssuerRetryableException(
                    action + " outcome unknown (" + t.reason() + "); retry - it is idempotent");
            case IssuerResult.Unavailable u -> throw new IssuerRetryableException(
                    action + " not sent (" + u.reason() + "); retry later");
        }
    }

    /** Data-level authorization: terminals and merchants only ever see their own merchant's payments. */
    private static void checkOwnership(Payment p, CurrentPrincipal caller) {
        if (caller.seesAllMerchants()) {
            return;
        }
        if (!p.getMerchantId().equals(caller.merchantId())) {
            throw new AccessDeniedException("Payment belongs to another merchant");
        }
        if (caller.has("TERMINAL") && !p.getTerminalId().equals(caller.terminalId())) {
            throw new AccessDeniedException("Payment belongs to another terminal");
        }
    }

    /** Retrieval Reference Number: 12 digits, assigned by the acquirer. */
    private static String newRrn() {
        return String.format("%012d", Math.floorMod(RANDOM.nextLong(), 1_000_000_000_000L));
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
