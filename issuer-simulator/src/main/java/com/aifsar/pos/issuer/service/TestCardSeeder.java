package com.aifsar.pos.issuer.service;

import com.aifsar.pos.issuer.domain.CardAccount;
import com.aifsar.pos.issuer.domain.CardAccount.Behaviour;
import com.aifsar.pos.issuer.domain.CardAccountRepository;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Test cards (amounts in minor units, i.e. paise/cents):
 *   4111111111111111  Visa, 5,00,000.00 available         -> approves
 *   5555555555554444  Mastercard, 2,00,000.00 available   -> approves
 *   4000000000000002  10.00 available                     -> 51 insufficient funds for most amounts
 *   4000000000000069  expired                             -> 54
 *   4000000000000119  slow issuer                         -> times out at payment-service, approves later
 *   anything else                                         -> 14 invalid card
 */
@Component
public class TestCardSeeder implements ApplicationRunner {

    private final CardAccountRepository cards;

    public TestCardSeeder(CardAccountRepository cards) {
        this.cards = cards;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (cards.count() > 0) {
            return;
        }
        cards.save(new CardAccount("4111111111111111", 50_000_000L, Behaviour.NORMAL));
        cards.save(new CardAccount("5555555555554444", 20_000_000L, Behaviour.NORMAL));
        cards.save(new CardAccount("4000000000000002", 1_000L, Behaviour.NORMAL));
        cards.save(new CardAccount("4000000000000069", 50_000_000L, Behaviour.EXPIRED));
        cards.save(new CardAccount("4000000000000119", 50_000_000L, Behaviour.SLOW));
    }
}
