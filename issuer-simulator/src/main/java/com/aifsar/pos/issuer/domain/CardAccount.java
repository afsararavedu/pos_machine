package com.aifsar.pos.issuer.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Cardholder account at the issuer. available = ledger - held.
 * An authorization moves money into "held"; capture moves it out of the ledger;
 * a reversal simply releases the hold.
 */
@Entity
@Table(name = "card_accounts")
public class CardAccount {

    public enum Behaviour { NORMAL, EXPIRED, SLOW }

    /** Test PANs only - a real issuer never keys on clear PAN. */
    @Id
    private String cardNumber;

    private long ledgerMinor;
    private long heldMinor;

    @Enumerated(EnumType.STRING)
    private Behaviour behaviour;

    protected CardAccount() {
    }

    public CardAccount(String cardNumber, long ledgerMinor, Behaviour behaviour) {
        this.cardNumber = cardNumber;
        this.ledgerMinor = ledgerMinor;
        this.behaviour = behaviour;
    }

    public long availableMinor() {
        return ledgerMinor - heldMinor;
    }

    public void hold(long amount) {
        heldMinor += amount;
    }

    public void release(long amount) {
        heldMinor -= amount;
    }

    public void capture(long amount) {
        heldMinor -= amount;
        ledgerMinor -= amount;
    }

    public String getCardNumber() { return cardNumber; }
    public long getLedgerMinor() { return ledgerMinor; }
    public long getHeldMinor() { return heldMinor; }
    public Behaviour getBehaviour() { return behaviour; }
}
