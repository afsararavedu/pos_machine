package com.aifsar.pos.payment.web;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import com.aifsar.pos.common.security.CurrentPrincipal;
import com.aifsar.pos.payment.service.AuthorizeCommand;
import com.aifsar.pos.payment.service.BatchCloseService;
import com.aifsar.pos.payment.service.BatchCloseService.BatchResult;
import com.aifsar.pos.payment.service.PaymentService;
import com.aifsar.pos.payment.service.PaymentService.PaymentOutcome;
import com.aifsar.pos.payment.web.PaymentDtos.AuthorizeRequest;
import com.aifsar.pos.payment.web.PaymentDtos.CaptureRequest;
import com.aifsar.pos.payment.web.PaymentDtos.PaymentResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Validated
public class PaymentController {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String REPLAYED = "Idempotent-Replayed";

    private final PaymentService payments;
    private final BatchCloseService batches;

    public PaymentController(PaymentService payments, BatchCloseService batches) {
        this.payments = payments;
        this.batches = batches;
    }

    /**
     * Card-present authorization. Only a TERMINAL token may call it, and only as itself.
     * 201 = processed now, 200 + Idempotent-Replayed: true = duplicate, original result returned.
     */
    @PostMapping("/payments")
    @PreAuthorize("hasRole('TERMINAL')")
    public ResponseEntity<PaymentResponse> authorize(
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody AuthorizeRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        CurrentPrincipal caller = CurrentPrincipal.from(jwt);
        if (!request.terminalId().equals(caller.terminalId())) {
            throw new AccessDeniedException("Token is not valid for terminal " + request.terminalId());
        }
        // Without a client key, fall back to the ISO 8583 natural key: STAN is unique per terminal per day.
        String key = idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey
                : caller.terminalId() + ":" + request.stan() + ":" + LocalDate.now(ZoneOffset.UTC);
        String scopedKey = caller.merchantId() + ":" + key; // keys never collide across merchants

        PaymentOutcome outcome = payments.authorize(new AuthorizeCommand(caller.merchantId(), request.terminalId(),
                request.stan(), request.amountMinor(), request.currency(), request.cardNumber(),
                request.entryMode()), scopedKey);

        return ResponseEntity.status(outcome.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header(REPLAYED, Boolean.toString(outcome.replayed()))
                .body(PaymentResponse.of(outcome.payment()));
    }

    @GetMapping("/payments/{id}")
    @PreAuthorize("hasAnyRole('TERMINAL','MERCHANT','ADMIN','AUDITOR')")
    public PaymentResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return PaymentResponse.of(payments.get(id, CurrentPrincipal.from(jwt)));
    }

    @PostMapping("/payments/{id}/capture")
    @PreAuthorize("hasAnyRole('MERCHANT','ADMIN')")
    public PaymentResponse capture(@PathVariable UUID id, @Valid @RequestBody(required = false) CaptureRequest body,
                                   @AuthenticationPrincipal Jwt jwt) {
        Long amount = body == null ? null : body.amountMinor();
        return PaymentResponse.of(payments.capture(id, amount, CurrentPrincipal.from(jwt)));
    }

    @PostMapping("/payments/{id}/void")
    @PreAuthorize("hasAnyRole('MERCHANT','ADMIN')")
    public PaymentResponse voidPayment(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return PaymentResponse.of(payments.voidPayment(id, CurrentPrincipal.from(jwt)));
    }

    /** End-of-day batch close. ADMIN only; optionally for one merchant. */
    @PostMapping("/batches/close")
    @PreAuthorize("hasRole('ADMIN')")
    public BatchResult closeBatch(@RequestParam(required = false) String merchantId) {
        return batches.close(merchantId);
    }
}
