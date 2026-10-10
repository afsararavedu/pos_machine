package com.aifsar.pos.reports.domain;

import java.time.Instant;
import java.util.UUID;

import com.aifsar.pos.common.event.TransactionEvent;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Read model: latest known state of each payment, built purely from events (CQRS projection). */
@Entity
@Table(name = "transaction_view")
public class TransactionView {

    @Id
    private UUID paymentId;
    private String merchantId;
    private String terminalId;
    private String stan;
    private String rrn;
    private String maskedPan;
    private String entryMode;
    private long amountMinor;
    private String currency;
    private String status;
    private String responseCode;
    private String responseMessage;
    private String authCode;
    /** How many duplicate submissions were blocked by idempotency for this payment. */
    private int duplicateAttempts;
    /** Highest event sequence applied; older/out-of-order events are ignored. */
    private long lastSequence;
    private Instant createdAt;
    private Instant updatedAt;

    protected TransactionView() {
    }

    public static TransactionView from(TransactionEvent e) {
        TransactionView v = new TransactionView();
        v.paymentId = e.paymentId();
        v.merchantId = e.merchantId();
        v.terminalId = e.terminalId();
        v.stan = e.stan();
        v.rrn = e.rrn();
        v.maskedPan = e.maskedPan();
        v.entryMode = e.entryMode();
        v.amountMinor = e.amountMinor();
        v.currency = e.currency();
        v.createdAt = e.occurredAt();
        v.lastSequence = -1;
        v.applyStatus(e);
        return v;
    }

    /** Returns false (and changes nothing) if the event is not newer than what we already have. */
    public boolean apply(TransactionEvent e) {
        if (e.sequence() <= lastSequence) {
            return false;
        }
        applyStatus(e);
        return true;
    }

    public void duplicateBlocked() {
        duplicateAttempts++;
    }

    private void applyStatus(TransactionEvent e) {
        status = e.status();
        responseCode = e.responseCode();
        responseMessage = e.responseMessage();
        authCode = e.authCode();
        lastSequence = e.sequence();
        updatedAt = e.occurredAt();
    }

    public UUID getPaymentId() { return paymentId; }
    public String getMerchantId() { return merchantId; }
    public String getTerminalId() { return terminalId; }
    public String getStan() { return stan; }
    public String getRrn() { return rrn; }
    public String getMaskedPan() { return maskedPan; }
    public String getEntryMode() { return entryMode; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public String getResponseCode() { return responseCode; }
    public String getResponseMessage() { return responseMessage; }
    public String getAuthCode() { return authCode; }
    public int getDuplicateAttempts() { return duplicateAttempts; }
    public long getLastSequence() { return lastSequence; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
