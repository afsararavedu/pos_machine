package com.aifsar.pos.reports.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionEventRecordRepository extends JpaRepository<TransactionEventRecord, UUID> {

    List<TransactionEventRecord> findByPaymentIdOrderBySequenceAscReceivedAtAsc(UUID paymentId);
}
