package com.aifsar.pos.issuer.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One row per (terminalId, stan, rrn). The unique constraint is the issuer-side
 * idempotency guard: a repeated authorization returns the original answer and never
 * places a second hold.
 */
@Entity
@Table(name = "issuer_authorizations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"terminal_id", "stan", "rrn"}))
public class IssuerAuthorization {

    public enum State {
        HELD,       // approved, funds on hold
        DECLINED,   // no hold placed
        CAPTURED,   // money taken from the ledger
        REVERSED    // hold released, or reversal arrived before the (late) authorization
    }

    @Id
    private UUID id;
    private String terminalId;
    private String stan;
    private String rrn;
    private String cardNumber;
    private long amountMinor;
    private String responseCode;
    private String authCode;

    @Enumerated(EnumType.STRING)
    private State state;

    private Instant createdAt;

    protected IssuerAuthorization() {
    }

    public IssuerAuthorization(String terminalId, String stan, String rrn, String cardNumber, long amountMinor,
                               String responseCode, String authCode, State state) {
        this.id = UUID.randomUUID();
        this.terminalId = terminalId;
        this.stan = stan;
        this.rrn = rrn;
        this.cardNumber = cardNumber;
        this.amountMinor = amountMinor;
        this.responseCode = responseCode;
        this.authCode = authCode;
        this.state = state;
        this.createdAt = Instant.now();
    }

    public void setState(State state) { this.state = state; }

    public UUID getId() { return id; }
    public String getCardNumber() { return cardNumber; }
    public long getAmountMinor() { return amountMinor; }
    public String getResponseCode() { return responseCode; }
    public String getAuthCode() { return authCode; }
    public State getState() { return state; }
}
