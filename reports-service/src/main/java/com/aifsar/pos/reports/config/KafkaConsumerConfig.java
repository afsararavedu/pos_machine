package com.aifsar.pos.reports.config;

import com.aifsar.pos.common.event.TransactionEvent;
import com.fasterxml.jackson.core.JsonProcessingException;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaConsumerConfig {

    public static final String DLT = TransactionEvent.TOPIC + "-dlt";

    /**
     * Transient failures (DB blip) are retried with backoff. Poison messages (unparseable JSON)
     * skip retries. Either way, after giving up the record goes to the dead-letter topic
     * instead of blocking the partition forever.
     */
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        ExponentialBackOff backOff = new ExponentialBackOff(500L, 2.0);
        backOff.setMaxElapsedTime(10_000L);
        DefaultErrorHandler handler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), backOff);
        handler.addNotRetryableExceptions(JsonProcessingException.class, IllegalArgumentException.class);
        return handler;
    }

    /** Same partition count as the main topic: the recoverer keeps the original partition number. */
    @Bean
    NewTopic deadLetterTopic(org.springframework.core.env.Environment env) {
        return TopicBuilder.name(DLT)
                .partitions(env.getProperty("reports.kafka.partitions", Integer.class, 3))
                .replicas(env.getProperty("reports.kafka.replicas", Integer.class, 1))
                .build();
    }
}
