package com.aifsar.pos.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event contract published by payment-service (via the transactional outbox) and
 * consumed by reports-service.
 *
 * eventId   - unique per event; consumers de-duplicate on it (Kafka is at-least-once).
 * sequence  - the payment row's version at the time of the change; consumers ignore
 *             an event whose sequence is not newer than what they already applied,
 *             so a late/out-of-order redelivery can never move a payment backwards.
 */
public record TransactionEvent(
        UUID eventId,
        UUID paymentId,
        String eventType,
        String status,
        String merchantId,
        String terminalId,
        String stan,
        String rrn,
        String maskedPan,
        String entryMode,
        long amountMinor,
        String currency,
        String responseCode,
        String responseMessage,
        String authCode,
        Instant occurredAt,
        long sequence) {

    public static final String TOPIC = "payments.transaction-events";

    public static final String PAYMENT_STATUS_CHANGED = "PAYMENT_STATUS_CHANGED";
    public static final String DUPLICATE_BLOCKED = "DUPLICATE_BLOCKED";
}
