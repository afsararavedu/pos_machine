package com.aifsar.pos.payment.web;

import java.time.Instant;
import java.util.UUID;

import com.aifsar.pos.payment.domain.EntryMode;
import com.aifsar.pos.payment.domain.Payment;
import com.aifsar.pos.payment.domain.PaymentStatus;
import com.aifsar.pos.payment.service.CardDataProtector;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    /**
     * What the POS sends. amountMinor is in the currency's minor unit (paise/cents) - never use
     * floating point for money. In a real terminal integration the PAN arrives encrypted (P2PE)
     * together with the EMV cryptogram; plain JSON here keeps the demo readable.
     */
    public record AuthorizeRequest(
            @NotBlank @Pattern(regexp = "[A-Z0-9]{1,16}") String terminalId,
            @NotBlank @Pattern(regexp = "\\d{6}") String stan,
            @Positive long amountMinor,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotBlank @Pattern(regexp = "\\d{13,19}") String cardNumber,
            @NotNull EntryMode entryMode) {

        @Override
        public String toString() {
            return "AuthorizeRequest[terminal=" + terminalId + ", stan=" + stan + ", amount=" + amountMinor + " "
                    + currency + ", card=" + (cardNumber == null || cardNumber.length() < 13 ? "?"
                    : CardDataProtector.mask(cardNumber)) + ", entryMode=" + entryMode + "]";
        }
    }

    public record CaptureRequest(@Positive Long amountMinor) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PaymentResponse(UUID paymentId, PaymentStatus status, boolean approved, String responseCode,
                                  String responseMessage, String authCode, String merchantId, String terminalId,
                                  String stan, String rrn, String maskedPan, String cardBrand, long amountMinor,
                                  Long capturedAmountMinor, String currency, Instant createdAt, Instant updatedAt) {

        static PaymentResponse of(Payment p) {
            boolean approved = switch (p.getStatus()) {
                case AUTHORIZED, CAPTURED, SETTLED -> true;
                default -> false;
            };
            return new PaymentResponse(p.getId(), p.getStatus(), approved, p.getResponseCode(),
                    p.getResponseMessage(), p.getAuthCode(), p.getMerchantId(), p.getTerminalId(), p.getStan(),
                    p.getRrn(), p.getMaskedPan(), p.getCardBrand(), p.getAmountMinor(), p.getCapturedAmountMinor(),
                    p.getCurrency(), p.getCreatedAt(), p.getUpdatedAt());
        }
    }
}
