package com.aifsar.pos.payment.service;

import java.util.concurrent.TimeUnit;

import com.aifsar.pos.common.event.TransactionEvent;
import com.aifsar.pos.payment.domain.OutboxEvent;
import com.aifsar.pos.payment.domain.OutboxEventRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes outbox rows to Kafka (at-least-once). If the pod dies after sending but before
 * marking a row published, the row is sent again - that is why consumers de-duplicate on eventId.
 */
@Component
@ConditionalOnProperty(prefix = "payments.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate tx;

    public OutboxRelay(OutboxEventRepository outbox, KafkaTemplate<String, String> kafka, TransactionTemplate tx) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${payments.outbox.interval-ms:500}")
    public void publish() {
        tx.executeWithoutResult(status -> {
            for (OutboxEvent event : outbox.lockUnpublished(100)) {
                try {
                    // key = paymentId keeps one payment's events ordered on a single partition
                    kafka.send(TransactionEvent.TOPIC, event.getAggregateId().toString(), event.getPayload())
                            .get(5, TimeUnit.SECONDS);
                    event.markPublished();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    // Stop at the first failure so later events of the same payment are not sent ahead of it.
                    log.warn("Kafka publish failed for event {}; will retry: {}", event.getId(), e.getMessage());
                    return;
                }
            }
        });
    }
}
