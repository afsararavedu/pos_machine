package com.aifsar.pos.payment.service;

import java.util.UUID;

/** Business exceptions mapped to HTTP status codes in ApiExceptionHandler. */
public final class PaymentExceptions {

    private PaymentExceptions() {
    }

    /** Same Idempotency-Key, different request body -> 422. Protects against client bugs reusing keys. */
    public static class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException(String key) {
            super("Idempotency-Key '" + key + "' was already used with a different request");
        }
    }

    /** Same key while the first attempt is still talking to the issuer -> 409 + Retry-After. */
    public static class RequestInProgressException extends RuntimeException {
        private final UUID paymentId;

        public RequestInProgressException(UUID paymentId) {
            super("A request with this Idempotency-Key is still being processed");
            this.paymentId = paymentId;
        }

        public UUID getPaymentId() {
            return paymentId;
        }
    }

    public static class PaymentNotFoundException extends RuntimeException {
        public PaymentNotFoundException(UUID id) {
            super("Payment not found: " + id);
        }
    }

    /** Capture/void could not reach a definite answer; safe for the client to retry (both are idempotent). */
    public static class IssuerRetryableException extends RuntimeException {
        public IssuerRetryableException(String message) {
            super(message);
        }
    }

    /** The issuer refused a capture/void (e.g. already captured). */
    public static class IssuerRejectedException extends RuntimeException {
        public IssuerRejectedException(String message) {
            super(message);
        }
    }
}
