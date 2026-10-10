package com.aifsar.pos.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.aifsar.pos.common.event.TransactionEvent;
import com.aifsar.pos.reports.domain.TransactionEventRecordRepository;
import com.aifsar.pos.reports.domain.TransactionView;
import com.aifsar.pos.reports.domain.TransactionViewRepository;
import com.aifsar.pos.reports.service.TransactionEventProcessor;
import com.aifsar.pos.reports.service.TransactionEventProcessor.Result;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TransactionEventProcessorTest {

    @Autowired TransactionEventProcessor processor;
    @Autowired TransactionViewRepository views;
    @Autowired TransactionEventRecordRepository events;

    private static TransactionEvent event(UUID paymentId, String type, String status, long seq) {
        return new TransactionEvent(UUID.randomUUID(), paymentId, type, status, "M1001", "T0001", "000123",
                "123456789012", "411111******1111", "CHIP", 250_00, "INR", "00", "Approved", "123456",
                Instant.now(), seq);
    }

    @Test
    void redeliveredEventIsAppliedOnlyOnce() {
        UUID paymentId = UUID.randomUUID();
        TransactionEvent authorized = event(paymentId, TransactionEvent.PAYMENT_STATUS_CHANGED, "AUTHORIZED", 1);

        assertThat(processor.apply(authorized)).isEqualTo(Result.APPLIED);
        assertThat(processor.apply(authorized)).isEqualTo(Result.DUPLICATE);   // Kafka redelivery
        assertThat(processor.apply(authorized)).isEqualTo(Result.DUPLICATE);

        assertThat(events.findByPaymentIdOrderBySequenceAscReceivedAtAsc(paymentId)).hasSize(1);
    }

    @Test
    void outOfOrderEventCannotMoveStatusBackwards() {
        UUID paymentId = UUID.randomUUID();
        processor.apply(event(paymentId, TransactionEvent.PAYMENT_STATUS_CHANGED, "INITIATED", 0));
        processor.apply(event(paymentId, TransactionEvent.PAYMENT_STATUS_CHANGED, "CAPTURED", 2));

        Result late = processor.apply(event(paymentId, TransactionEvent.PAYMENT_STATUS_CHANGED, "AUTHORIZED", 1));

        assertThat(late).isEqualTo(Result.STALE);
        assertThat(views.findById(paymentId)).map(TransactionView::getStatus).contains("CAPTURED");
    }

    @Test
    void blockedDuplicatesAreCountedWithoutChangingStatus() {
        UUID paymentId = UUID.randomUUID();
        processor.apply(event(paymentId, TransactionEvent.PAYMENT_STATUS_CHANGED, "AUTHORIZED", 1));
        processor.apply(event(paymentId, TransactionEvent.DUPLICATE_BLOCKED, "AUTHORIZED", 1));
        processor.apply(event(paymentId, TransactionEvent.DUPLICATE_BLOCKED, "AUTHORIZED", 1));

        TransactionView view = views.findById(paymentId).orElseThrow();
        assertThat(view.getDuplicateAttempts()).isEqualTo(2);
        assertThat(view.getStatus()).isEqualTo("AUTHORIZED");
    }
}
