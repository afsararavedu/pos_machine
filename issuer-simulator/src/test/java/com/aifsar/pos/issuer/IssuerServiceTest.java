package com.aifsar.pos.issuer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.aifsar.pos.issuer.domain.CardAccountRepository;
import com.aifsar.pos.issuer.service.IssuerService;
import com.aifsar.pos.issuer.service.IssuerService.AuthorizationRequest;
import com.aifsar.pos.issuer.service.IssuerService.IssuerResponse;
import com.aifsar.pos.issuer.service.IssuerService.ReferenceRequest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "issuer.slow-card-delay=0s")
class IssuerServiceTest {

    @Autowired IssuerService issuer;
    @Autowired CardAccountRepository cards;

    @Test
    void concurrentDuplicateAuthorizationsPlaceOnlyOneHold() throws Exception {
        String card = "5555555555554444";
        long heldBefore = cards.findById(card).orElseThrow().getHeldMinor();
        var request = new AuthorizationRequest("T1", "000123", "100000000001", card, 5_000, "INR");

        CountDownLatch start = new CountDownLatch(1);
        List<Future<IssuerResponse>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 20; i++) {
                Callable<IssuerResponse> call = () -> {
                    start.await();
                    return issuer.authorize(request);
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            for (Future<IssuerResponse> f : results) {
                assertThat(f.get().responseCode()).isEqualTo("00");
            }
        }
        long authCodes = results.stream().map(f -> f.resultNow().authCode()).distinct().count();
        assertThat(authCodes).isEqualTo(1);
        assertThat(cards.findById(card).orElseThrow().getHeldMinor()).isEqualTo(heldBefore + 5_000);
    }

    @Test
    void reversalThatOvertakesTheAuthorizationPreventsTheLateHold() {
        String card = "4111111111111111";
        long heldBefore = cards.findById(card).orElseThrow().getHeldMinor();

        issuer.reverse(new ReferenceRequest("T1", "000999", "100000000999", 7_000));
        IssuerResponse late = issuer.authorize(
                new AuthorizationRequest("T1", "000999", "100000000999", card, 7_000, "INR"));

        assertThat(late.state()).isEqualTo("REVERSED");
        assertThat(cards.findById(card).orElseThrow().getHeldMinor()).isEqualTo(heldBefore);
    }
}
