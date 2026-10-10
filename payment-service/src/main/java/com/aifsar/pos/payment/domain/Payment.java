package com.aifsar.pos.payment.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * One logical payment attempt. The unique idempotency_key column (see V1 migration) is the
 * concurrency guard: only one thread/pod can ever insert a given key.
 * The card number is never stored - only an HMAC token (for matching) and the masked PAN.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    /** SHA-256 of the business fields; a reused key with a different body is rejected. */
    @Column(nullable = false)
    private String requestHash;

    private String merchantId;
    private String terminalId;
    private String stan;
    private String rrn;
    private String panToken;
    private String maskedPan;
    private String cardBrand;

    @Enumerated(EnumType.STRING)
    private EntryMode entryMode;

    private long amountMinor;
    private Long capturedAmountMinor;
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    private String responseCode;
    private String responseMessage;
    private String authCode;
    private int reversalAttempts;
    private Instant createdAt;
    private Instant updatedAt;

    /** Optimistic lock + event sequence number. Wrapper type so a fresh entity is persisted, not merged. */
    @Version
    private Long version;

    protected Payment() {
    }

    public static Payment initiate(String idempotencyKey, String requestHash, String merchantId, String terminalId,
                                   String stan, String rrn, String panToken, String maskedPan, String cardBrand,
                                   EntryMode entryMode, long amountMinor, String currency) {
        Payment p = new Payment();
        p.id = UUID.randomUUID();
        p.idempotencyKey = idempotencyKey;
        p.requestHash = requestHash;
        p.merchantId = merchantId;
        p.terminalId = terminalId;
        p.stan = stan;
        p.rrn = rrn;
        p.panToken = panToken;
        p.maskedPan = maskedPan;
        p.cardBrand = cardBrand;
        p.entryMode = entryMode;
        p.amountMinor = amountMinor;
        p.currency = currency;
        p.status = PaymentStatus.INITIATED;
        p.createdAt = Instant.now();
        p.updatedAt = p.createdAt;
        return p;
    }

    public void authorize(String authCode) {
        moveTo(PaymentStatus.AUTHORIZED, "00", "Approved");
        this.authCode = authCode;
    }

    public void decline(String code, String message) {
        moveTo(PaymentStatus.DECLINED, code, message);
    }

    /** Outcome unknown (timeout, lost response, crash). Funds may be on hold, so we must reverse. */
    public void markPendingReversal(String reason) {
        moveTo(PaymentStatus.TIMEOUT_PENDING_REVERSAL, "91", reason);
    }

    public void reversed() {
        moveTo(PaymentStatus.REVERSED, "91", "Not completed; any hold has been released");
    }

    public void voided() {
        moveTo(PaymentStatus.VOIDED, responseCode, "Voided by merchant");
    }

    public void captured(long amount) {
        moveTo(PaymentStatus.CAPTURED, responseCode, "Captured");
        this.capturedAmountMinor = amount;
    }

    public void settled() {
        moveTo(PaymentStatus.SETTLED, responseCode, "Settled");
    }

    public void reversalAttemptFailed() {
        reversalAttempts++;
        updatedAt = Instant.now();
    }

    private void moveTo(PaymentStatus next, String code, String message) {
        if (!status.canTransitionTo(next)) {
            throw new InvalidTransitionException(id, status, next);
        }
        this.status = next;
        this.responseCode = code;
        this.responseMessage = message;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public String getMerchantId() { return merchantId; }
    public String getTerminalId() { return terminalId; }
    public String getStan() { return stan; }
    public String getRrn() { return rrn; }
    public String getPanToken() { return panToken; }
    public String getMaskedPan() { return maskedPan; }
    public String getCardBrand() { return cardBrand; }
    public EntryMode getEntryMode() { return entryMode; }
    public long getAmountMinor() { return amountMinor; }
    public Long getCapturedAmountMinor() { return capturedAmountMinor; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public String getResponseCode() { return responseCode; }
    public String getResponseMessage() { return responseMessage; }
    public String getAuthCode() { return authCode; }
    public int getReversalAttempts() { return reversalAttempts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
