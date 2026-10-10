package com.aifsar.pos.payment.domain;

import java.util.UUID;

public class InvalidTransitionException extends RuntimeException {

    private final PaymentStatus current;

    public InvalidTransitionException(UUID paymentId, PaymentStatus current, PaymentStatus next) {
        super("Payment " + paymentId + " cannot move from " + current + " to " + next);
        this.current = current;
    }

    public PaymentStatus getCurrent() {
        return current;
    }
}
