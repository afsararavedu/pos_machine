package com.aifsar.pos.payment.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payments")
public record PaymentProperties(Issuer issuer, Card card, Reversal reversal, Kafka kafka) {

    /** readTimeout is the "how long do we wait before assuming the response is lost" knob. */
    public record Issuer(String baseUrl, Duration connectTimeout, Duration readTimeout) {
    }

    /** HMAC key for PAN tokenisation. Must come from a secret / KMS outside local dev. */
    public record Card(String tokenSecret) {
    }

    /**
     * maxAttempts         - give up auto-reversal after this many tries and leave it for reconciliation.
     * stuckInitiatedAfter - an INITIATED payment older than this means the service died mid-call:
     *                       we cannot know if the issuer approved, so we reverse to be safe.
     */
    public record Reversal(int maxAttempts, Duration stuckInitiatedAfter) {
    }

    public record Kafka(int partitions, int replicas) {
    }
}
