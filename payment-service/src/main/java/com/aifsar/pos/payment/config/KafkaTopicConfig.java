package com.aifsar.pos.payment.config;

import com.aifsar.pos.common.event.TransactionEvent;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    /** Keyed by paymentId, so all events of one payment land on one partition in order. */
    @Bean
    NewTopic transactionEventsTopic(PaymentProperties props) {
        return TopicBuilder.name(TransactionEvent.TOPIC)
                .partitions(props.kafka().partitions())
                .replicas(props.kafka().replicas())
                .build();
    }
}
