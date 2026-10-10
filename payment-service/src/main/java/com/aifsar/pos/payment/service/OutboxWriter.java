package com.aifsar.pos.payment.service;

import java.time.Instant;
import java.util.UUID;

import com.aifsar.pos.common.event.TransactionEvent;
import com.aifsar.pos.payment.domain.OutboxEvent;
import com.aifsar.pos.payment.domain.OutboxEventRepository;
import com.aifsar.pos.payment.domain.Payment;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Must be called inside the transaction that changed the payment (MANDATORY enforces it). */
@Component
public class OutboxWriter {

    private final OutboxEventRepository outbox;
    private final ObjectMapper mapper;

    public OutboxWriter(OutboxEventRepository outbox, ObjectMapper mapper) {
        this.outbox = outbox;
        this.mapper = mapper;
    }

    /** Call after saveAndFlush so payment.getVersion() is the new version (used as the sequence). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Payment p, String eventType) {
        UUID eventId = UUID.randomUUID();
        TransactionEvent event = new TransactionEvent(eventId, p.getId(), eventType, p.getStatus().name(),
                p.getMerchantId(), p.getTerminalId(), p.getStan(), p.getRrn(), p.getMaskedPan(),
                p.getEntryMode().name(), p.getAmountMinor(), p.getCurrency(), p.getResponseCode(),
                p.getResponseMessage(), p.getAuthCode(), Instant.now(), p.getVersion() == null ? 0 : p.getVersion());
        try {
            outbox.save(new OutboxEvent(eventId, p.getId(), eventType, mapper.writeValueAsString(event)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise event", e);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void statusChanged(Payment p) {
        record(p, TransactionEvent.PAYMENT_STATUS_CHANGED);
    }
}
