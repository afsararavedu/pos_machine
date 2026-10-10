package com.aifsar.pos.reports.domain;

import java.time.Instant;
import java.util.UUID;

import com.aifsar.pos.common.event.TransactionEvent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Immutable audit trail of every event received. Its primary key is the eventId, so this table
 * doubles as the idempotent-consumer ledger: an event already here has been processed.
 */
@Entity
@Table(name = "transaction_events")
public class TransactionEventRecord {

    @Id
    private UUID eventId;
    private UUID paymentId;
    private String eventType;
    private String status;
    private String responseCode;
    private String responseMessage;
    @Column(name = "event_sequence")
    private long sequence;
    private Instant occurredAt;
    private Instant receivedAt;

    protected TransactionEventRecord() {
    }

    public static TransactionEventRecord from(TransactionEvent e) {
        TransactionEventRecord r = new TransactionEventRecord();
        r.eventId = e.eventId();
        r.paymentId = e.paymentId();
        r.eventType = e.eventType();
        r.status = e.status();
        r.responseCode = e.responseCode();
        r.responseMessage = e.responseMessage();
        r.sequence = e.sequence();
        r.occurredAt = e.occurredAt();
        r.receivedAt = Instant.now();
        return r;
    }

    public UUID getEventId() { return eventId; }
    public UUID getPaymentId() { return paymentId; }
    public String getEventType() { return eventType; }
    public String getStatus() { return status; }
    public String getResponseCode() { return responseCode; }
    public String getResponseMessage() { return responseMessage; }
    public long getSequence() { return sequence; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getReceivedAt() { return receivedAt; }
}
