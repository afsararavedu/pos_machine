package com.aifsar.pos.payment.issuer;

/**
 * Every possible outcome of talking to the issuer, as a closed (sealed) set.
 * The compiler forces each caller's switch to handle all four, which is exactly
 * what you want for money movement: no "forgot the timeout case" bugs.
 */
public sealed interface IssuerResult {

    /** Issuer said yes (for an authorization) or acknowledged (for a reversal/capture). */
    record Approved(String authCode, String code, String message) implements IssuerResult {
    }

    /** Issuer said no. Definitive: nothing is on hold, nothing to undo. */
    record Declined(String code, String message) implements IssuerResult {
    }

    /** We sent the request but do not know the outcome. Funds MAY be held, so reverse. */
    record TimedOut(String reason) implements IssuerResult {
    }

    /** The request never left (circuit open, connection refused). Safe to treat as a decline. */
    record Unavailable(String reason) implements IssuerResult {
    }
}
