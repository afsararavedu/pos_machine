package com.aifsar.pos.issuer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Simulates the card network + issuing bank so the payment flow can be exercised end to end.
 * In production this is replaced by an ISO 8583 connection (e.g. jPOS) to the acquirer/network.
 */
@SpringBootApplication
public class IssuerSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(IssuerSimulatorApplication.class, args);
    }
}
