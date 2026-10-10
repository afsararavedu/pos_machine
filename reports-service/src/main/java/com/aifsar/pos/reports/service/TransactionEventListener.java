package com.aifsar.pos.reports.service;

import com.aifsar.pos.common.event.TransactionEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TransactionEventListener {

    private final ObjectMapper mapper;
    private final TransactionEventProcessor processor;

    public TransactionEventListener(ObjectMapper mapper, TransactionEventProcessor processor) {
        this.mapper = mapper;
        this.processor = processor;
    }

    /** Offsets are committed only after apply() returns, i.e. after the DB transaction commits. */
    @KafkaListener(topics = TransactionEvent.TOPIC, groupId = "reports-service")
    public void onEvent(String payload) throws JsonProcessingException {
        processor.apply(mapper.readValue(payload, TransactionEvent.class));
    }
}
