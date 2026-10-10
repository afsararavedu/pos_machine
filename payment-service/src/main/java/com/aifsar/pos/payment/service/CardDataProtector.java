package com.aifsar.pos.payment.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.aifsar.pos.payment.config.PaymentProperties;

import org.springframework.stereotype.Component;

/**
 * PCI-DSS basics: the PAN is used in memory only to call the issuer. What we persist is
 * an HMAC token (lets us match the same card without storing it) and a masked PAN.
 * In production the token/key live in a vault/HSM and PAN arrives encrypted from the terminal.
 */
@Component
public class CardDataProtector {

    private final byte[] key;

    public CardDataProtector(PaymentProperties props) {
        this.key = props.card().tokenSecret().getBytes(StandardCharsets.UTF_8);
    }

    public String tokenize(String pan) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(pan.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String mask(String pan) {
        return pan.substring(0, 6) + "*".repeat(pan.length() - 10) + pan.substring(pan.length() - 4);
    }

    public static String brand(String pan) {
        return switch (pan.charAt(0)) {
            case '4' -> "VISA";
            case '5', '2' -> "MASTERCARD";
            case '6' -> "RUPAY";
            case '3' -> "AMEX";
            default -> "UNKNOWN";
        };
    }

    /** Luhn check catches most mistyped/garbled card numbers before we bother the issuer. */
    public static boolean luhnValid(String pan) {
        int sum = 0;
        boolean dbl = false;
        for (int i = pan.length() - 1; i >= 0; i--) {
            int d = pan.charAt(i) - '0';
            if (dbl) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }
}
