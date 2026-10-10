package com.aifsar.pos.common.security;

import java.util.Collection;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Shared helpers so every resource server validates tokens the same way:
 * signature via the auth-service JWKS, expiry, issuer and audience, and the
 * "roles" claim mapped to ROLE_* authorities.
 */
public final class JwtSecuritySupport {

    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_MERCHANT_ID = "merchant_id";
    public static final String CLAIM_TERMINAL_ID = "terminal_id";

    private JwtSecuritySupport() {
    }

    public static JwtDecoder jwkSetDecoder(String jwkSetUri, String issuer, String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(validator(issuer, audience));
        return decoder;
    }

    public static OAuth2TokenValidator<Jwt> validator(String issuer, String audience) {
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience));
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), audienceValidator);
    }

    public static Converter<Jwt, AbstractAuthenticationToken> rolesConverter() {
        return jwt -> {
            List<String> roles = jwt.getClaimAsStringList(CLAIM_ROLES);
            Collection<GrantedAuthority> authorities = roles == null ? List.of()
                    : roles.stream().<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        };
    }
}
