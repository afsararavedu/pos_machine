package com.aifsar.pos.payment.issuer;

import java.net.ConnectException;
import java.util.function.Predicate;

import com.aifsar.pos.payment.config.PaymentProperties;
import com.aifsar.pos.payment.domain.Payment;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP stand-in for the ISO 8583 link to the network/issuer.
 *
 * Rules that keep money safe:
 *  1. Explicit connect/read timeouts - never wait forever.
 *  2. NO automatic retry of an authorization. A retry is a new message the issuer may also
 *     approve. Ambiguity is resolved by reversing, not by retrying.
 *  3. Classify failures: "never sent" (safe decline) vs "sent, answer unknown" (must reverse).
 *  4. Circuit breaker so a dead issuer fails fast instead of tying up every terminal.
 */
@Component
public class IssuerClient {

    private static final Logger log = LoggerFactory.getLogger(IssuerClient.class);

    public record IssuerAuthRequest(String terminalId, String stan, String rrn, String cardNumber, long amountMinor,
                             String currency) {
        @Override
        public String toString() {
            return "IssuerAuthRequest[stan=" + stan + ", rrn=" + rrn + ", amount=" + amountMinor + "]";
        }
    }

    public record IssuerReferenceRequest(String terminalId, String stan, String rrn, long amountMinor) {
    }

    public record IssuerResponse(String responseCode, String responseMessage, String authCode, String state) {
    }

    private final RestClient rest;
    private final CircuitBreaker circuitBreaker;

    public IssuerClient(RestClient.Builder builder, PaymentProperties props, CircuitBreakerRegistry registry) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.issuer().connectTimeout());
        factory.setReadTimeout(props.issuer().readTimeout());
        this.rest = builder.baseUrl(props.issuer().baseUrl()).requestFactory(factory).build();
        this.circuitBreaker = registry.circuitBreaker("issuer");
    }

    public IssuerResult authorize(Payment p, String cardNumber) {
        var body = new IssuerAuthRequest(p.getTerminalId(), p.getStan(), p.getRrn(), cardNumber, p.getAmountMinor(),
                p.getCurrency());
        return call("/issuer/authorizations", body, response ->
                "00".equals(response.responseCode()) && "HELD".equals(response.state()));
    }

    public IssuerResult reverse(Payment p) {
        var body = new IssuerReferenceRequest(p.getTerminalId(), p.getStan(), p.getRrn(), p.getAmountMinor());
        return call("/issuer/reversals", body, response -> "00".equals(response.responseCode()));
    }

    public IssuerResult capture(Payment p, long amountMinor) {
        var body = new IssuerReferenceRequest(p.getTerminalId(), p.getStan(), p.getRrn(), amountMinor);
        return call("/issuer/captures", body, response -> "00".equals(response.responseCode()));
    }

    private IssuerResult call(String path, Object body, Predicate<IssuerResponse> success) {
        try {
            IssuerResponse response = circuitBreaker.executeSupplier(() ->
                    rest.post().uri(path).body(body).retrieve().body(IssuerResponse.class));
            if (response == null) {
                return new IssuerResult.TimedOut("Empty issuer response");
            }
            return success.test(response)
                    ? new IssuerResult.Approved(response.authCode(), response.responseCode(), response.responseMessage())
                    : new IssuerResult.Declined(response.responseCode(), response.responseMessage());
        } catch (CallNotPermittedException open) {
            log.warn("Issuer circuit open; {} not sent", path);
            return new IssuerResult.Unavailable("Issuer circuit open");
        } catch (ResourceAccessException io) {
            if (io.getCause() instanceof ConnectException) {
                log.warn("Issuer refused connection on {}; request not sent", path);
                return new IssuerResult.Unavailable("Issuer unreachable");
            }
            log.warn("No answer from issuer on {} ({}); outcome unknown", path, io.getMessage());
            return new IssuerResult.TimedOut("No response from issuer");
        } catch (RestClientException e) {
            // e.g. HTTP 5xx: the issuer may have processed it before failing. Treat as unknown.
            log.warn("Issuer error on {}: {}; outcome unknown", path, e.getMessage());
            return new IssuerResult.TimedOut("Issuer error");
        }
    }
}
