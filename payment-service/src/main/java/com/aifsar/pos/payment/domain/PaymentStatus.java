package com.aifsar.pos.payment.domain;

/**
 * Payment state machine. Only the transitions listed in {@link #canTransitionTo} are legal;
 * anything else is a bug or a race and is rejected.
 *
 * <pre>
 * INITIATED ──► AUTHORIZED ──► CAPTURED ──► SETTLED
 *    │              └──► VOIDED            (merchant cancels before capture)
 *    ├──► DECLINED
 *    └──► TIMEOUT_PENDING_REVERSAL ──► REVERSED
 * </pre>
 */
public enum PaymentStatus {
    INITIATED,
    AUTHORIZED,
    DECLINED,
    TIMEOUT_PENDING_REVERSAL,
    REVERSED,
    VOIDED,
    CAPTURED,
    SETTLED;

    public boolean canTransitionTo(PaymentStatus next) {
        return switch (this) {
            case INITIATED -> next == AUTHORIZED || next == DECLINED || next == TIMEOUT_PENDING_REVERSAL;
            case AUTHORIZED -> next == CAPTURED || next == VOIDED;
            case TIMEOUT_PENDING_REVERSAL -> next == REVERSED;
            case CAPTURED -> next == SETTLED;
            case DECLINED, REVERSED, VOIDED, SETTLED -> false;
        };
    }

    public boolean isFinal() {
        return this == DECLINED || this == REVERSED || this == VOIDED || this == SETTLED;
    }
}
