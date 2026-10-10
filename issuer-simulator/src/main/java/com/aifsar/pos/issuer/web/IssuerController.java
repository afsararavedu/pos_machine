package com.aifsar.pos.issuer.web;

import java.util.List;

import com.aifsar.pos.issuer.domain.CardAccount;
import com.aifsar.pos.issuer.domain.CardAccountRepository;
import com.aifsar.pos.issuer.service.IssuerService;
import com.aifsar.pos.issuer.service.IssuerService.AuthorizationRequest;
import com.aifsar.pos.issuer.service.IssuerService.IssuerResponse;
import com.aifsar.pos.issuer.service.IssuerService.ReferenceRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal API, reachable only from payment-service (enforced by a Kubernetes NetworkPolicy;
 * in production this hop is ISO 8583 over a private link / mTLS).
 */
@RestController
@RequestMapping("/issuer")
public class IssuerController {

    public record AccountView(String card, long ledgerMinor, long heldMinor, long availableMinor, String behaviour) {
        static AccountView of(CardAccount c) {
            String pan = c.getCardNumber();
            return new AccountView(pan.substring(0, 6) + "******" + pan.substring(pan.length() - 4),
                    c.getLedgerMinor(), c.getHeldMinor(), c.availableMinor(), c.getBehaviour().name());
        }
    }

    private final IssuerService issuer;
    private final CardAccountRepository cards;

    public IssuerController(IssuerService issuer, CardAccountRepository cards) {
        this.issuer = issuer;
        this.cards = cards;
    }

    @PostMapping("/authorizations")
    public IssuerResponse authorize(@RequestBody AuthorizationRequest request) {
        return issuer.authorize(request);
    }

    @PostMapping("/reversals")
    public IssuerResponse reverse(@RequestBody ReferenceRequest request) {
        return issuer.reverse(request);
    }

    @PostMapping("/captures")
    public IssuerResponse capture(@RequestBody ReferenceRequest request) {
        return issuer.capture(request);
    }

    /** Lets you watch holds appear and disappear while running the demo. */
    @GetMapping("/accounts")
    public List<AccountView> accounts() {
        return cards.findAll().stream().map(AccountView::of).toList();
    }
}
