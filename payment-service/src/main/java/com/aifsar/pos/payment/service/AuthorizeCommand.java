package com.aifsar.pos.payment.service;

import com.aifsar.pos.payment.domain.EntryMode;

/** Validated input for an authorization. toString is overridden so the PAN can never reach a log. */
public record AuthorizeCommand(String merchantId, String terminalId, String stan, long amountMinor, String currency,
                               String cardNumber, EntryMode entryMode) {

    @Override
    public String toString() {
        return "AuthorizeCommand[merchant=" + merchantId + ", terminal=" + terminalId + ", stan=" + stan
                + ", amount=" + amountMinor + " " + currency + ", card=" + CardDataProtector.mask(cardNumber)
                + ", entryMode=" + entryMode + "]";
    }
}
