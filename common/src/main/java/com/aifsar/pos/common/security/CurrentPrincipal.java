package com.aifsar.pos.common.security;

import java.util.List;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Typed view of the authenticated caller, built from the validated JWT.
 * Used for data-level authorization (a merchant only sees its own data,
 * a terminal can only transact as itself).
 */
public record CurrentPrincipal(String subject, List<String> roles, String merchantId, String terminalId) {

    public static CurrentPrincipal from(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(JwtSecuritySupport.CLAIM_ROLES);
        return new CurrentPrincipal(
                jwt.getSubject(),
                roles == null ? List.of() : roles,
                jwt.getClaimAsString(JwtSecuritySupport.CLAIM_MERCHANT_ID),
                jwt.getClaimAsString(JwtSecuritySupport.CLAIM_TERMINAL_ID));
    }

    public boolean has(String role) {
        return roles.contains(role);
    }

    /** ADMIN and AUDITOR can see every merchant; everyone else is scoped to their own. */
    public boolean seesAllMerchants() {
        return has("ADMIN") || has("AUDITOR");
    }
}
