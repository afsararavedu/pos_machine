package com.aifsar.pos.issuer.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.function.Supplier;

import com.aifsar.pos.issuer.domain.CardAccount;
import com.aifsar.pos.issuer.domain.CardAccountRepository;
import com.aifsar.pos.issuer.domain.IssuerAuthorization;
import com.aifsar.pos.issuer.domain.IssuerAuthorization.State;
import com.aifsar.pos.issuer.domain.IssuerAuthorizationRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IssuerService {

    private static final Logger log = LoggerFactory.getLogger(IssuerService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    public record AuthorizationRequest(String terminalId, String stan, String rrn, String cardNumber,
                                       long amountMinor, String currency) {
    }

    public record ReferenceRequest(String terminalId, String stan, String rrn, long amountMinor) {
    }

    public record IssuerResponse(String responseCode, String responseMessage, String authCode, String state) {
    }

    private final CardAccountRepository cards;
    private final IssuerAuthorizationRepository authorizations;
    private final TransactionTemplate tx;
    private final Duration slowCardDelay;

    public IssuerService(CardAccountRepository cards, IssuerAuthorizationRepository authorizations,
                         TransactionTemplate tx, @Value("${issuer.slow-card-delay:8s}") Duration slowCardDelay) {
        this.cards = cards;
        this.authorizations = authorizations;
        this.tx = tx;
        this.slowCardDelay = slowCardDelay;
    }

    /**
     * Idempotent per (terminalId, stan, rrn): a repeat returns the stored answer and never
     * places a second hold. The account row is locked so concurrent authorizations on one
     * card cannot overspend the balance.
     */
    public IssuerResponse authorize(AuthorizationRequest r) {
        boolean slow = cards.findById(r.cardNumber())
                .map(c -> c.getBehaviour() == CardAccount.Behaviour.SLOW).orElse(false);
        if (slow) {
            // The caller gives up before we answer, but we still approve and hold funds afterwards:
            // exactly the "approved but response lost" case that reversals exist for.
            sleep(slowCardDelay);
        }
        return retryOnConflict(() -> tx.execute(status -> doAuthorize(r)));
    }

    private IssuerResponse doAuthorize(AuthorizationRequest r) {
        CardAccount account = cards.lock(r.cardNumber()).orElse(null);
        var existing = authorizations.findForUpdate(r.terminalId(), r.stan(), r.rrn());
        if (existing.isPresent()) {
            log.info("Duplicate authorization stan={} rrn={} -> replaying {}", r.stan(), r.rrn(),
                    existing.get().getState());
            return toResponse(existing.get());
        }

        String code;
        State state = State.DECLINED;
        String authCode = null;
        if (account == null) {
            code = "14";
        } else if (account.getBehaviour() == CardAccount.Behaviour.EXPIRED) {
            code = "54";
        } else if (account.availableMinor() < r.amountMinor()) {
            code = "51";
        } else {
            account.hold(r.amountMinor());
            code = "00";
            state = State.HELD;
            authCode = String.format("%06d", RANDOM.nextInt(1_000_000));
        }
        IssuerAuthorization saved = authorizations.saveAndFlush(new IssuerAuthorization(
                r.terminalId(), r.stan(), r.rrn(), r.cardNumber(), r.amountMinor(), code, authCode, state));
        log.info("Authorization stan={} rrn={} amount={} -> {}", r.stan(), r.rrn(), r.amountMinor(), code);
        return toResponse(saved);
    }

    /**
     * Idempotent. If the reversal overtakes a slow authorization, a REVERSED tombstone is stored,
     * so when the late authorization finally arrives it replays "reversed" instead of holding funds.
     */
    public IssuerResponse reverse(ReferenceRequest r) {
        return retryOnConflict(() -> tx.execute(status -> {
            var existing = authorizations.findForUpdate(r.terminalId(), r.stan(), r.rrn());
            if (existing.isEmpty()) {
                authorizations.saveAndFlush(new IssuerAuthorization(r.terminalId(), r.stan(), r.rrn(), null,
                        r.amountMinor(), "12", null, State.REVERSED));
                log.info("Reversal before authorization stan={} rrn={}: tombstone stored", r.stan(), r.rrn());
                return new IssuerResponse("00", "Reversal recorded", null, State.REVERSED.name());
            }
            IssuerAuthorization auth = existing.get();
            return switch (auth.getState()) {
                case HELD -> {
                    cards.lock(auth.getCardNumber()).ifPresent(c -> c.release(auth.getAmountMinor()));
                    auth.setState(State.REVERSED);
                    log.info("Reversal stan={} rrn={}: hold of {} released", r.stan(), r.rrn(),
                            auth.getAmountMinor());
                    yield new IssuerResponse("00", "Hold released", auth.getAuthCode(), State.REVERSED.name());
                }
                case REVERSED -> new IssuerResponse("00", "Already reversed", auth.getAuthCode(), "REVERSED");
                case DECLINED -> new IssuerResponse("00", "Nothing to reverse (declined)", null, "DECLINED");
                case CAPTURED -> new IssuerResponse("12", "Already captured; use a refund", auth.getAuthCode(),
                        "CAPTURED");
            };
        }));
    }

    /** Idempotent. Supports partial capture: any uncaptured remainder of the hold is released. */
    public IssuerResponse capture(ReferenceRequest r) {
        return tx.execute(status -> {
            var existing = authorizations.findForUpdate(r.terminalId(), r.stan(), r.rrn());
            if (existing.isEmpty()) {
                return new IssuerResponse("12", "Unknown authorization", null, null);
            }
            IssuerAuthorization auth = existing.get();
            return switch (auth.getState()) {
                case HELD -> {
                    if (r.amountMinor() > auth.getAmountMinor()) {
                        yield new IssuerResponse("13", "Capture exceeds authorized amount", auth.getAuthCode(),
                                "HELD");
                    }
                    cards.lock(auth.getCardNumber()).ifPresent(c -> {
                        c.capture(r.amountMinor());
                        c.release(auth.getAmountMinor() - r.amountMinor());
                    });
                    auth.setState(State.CAPTURED);
                    yield new IssuerResponse("00", "Captured", auth.getAuthCode(), "CAPTURED");
                }
                case CAPTURED -> new IssuerResponse("00", "Already captured", auth.getAuthCode(), "CAPTURED");
                case REVERSED, DECLINED -> new IssuerResponse("12", "Not capturable: " + auth.getState(),
                        auth.getAuthCode(), auth.getState().name());
            };
        });
    }

    private static IssuerResponse toResponse(IssuerAuthorization a) {
        return new IssuerResponse(a.getResponseCode(), message(a), a.getAuthCode(), a.getState().name());
    }

    private static String message(IssuerAuthorization a) {
        if (a.getState() == State.REVERSED) {
            return "Transaction already reversed";
        }
        return switch (a.getResponseCode()) {
            case "00" -> "Approved";
            case "14" -> "Invalid card number";
            case "51" -> "Insufficient funds";
            case "54" -> "Expired card";
            default -> "Declined";
        };
    }

    /** Two concurrent first-time requests for the same key: the loser hits the unique constraint and re-reads. */
    private static IssuerResponse retryOnConflict(Supplier<IssuerResponse> action) {
        try {
            return action.get();
        } catch (DataIntegrityViolationException | ConcurrencyFailureException race) {
            return action.get();
        }
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
