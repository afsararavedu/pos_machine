package com.aifsar.pos.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import com.aifsar.pos.auth.domain.Role;
import com.aifsar.pos.auth.domain.UserAccount.AccountType;
import com.aifsar.pos.auth.service.AuthFailedException;
import com.aifsar.pos.auth.service.AuthService;
import com.aifsar.pos.auth.service.TokenResponse;
import com.aifsar.pos.auth.service.UserAdminService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AuthServiceTest {

    @Autowired AuthService auth;
    @Autowired UserAdminService admin;
    @Autowired JwtDecoder decoder;

    @Test
    void terminalTokenCarriesMerchantAndTerminalClaims() {
        admin.create("T9001", "Secret@123", AccountType.TERMINAL, Set.of(Role.TERMINAL), "M9", "T9001");

        TokenResponse token = auth.terminalToken("T9001", "Secret@123");
        Jwt jwt = decoder.decode(token.accessToken());

        assertThat(jwt.getClaimAsString("merchant_id")).isEqualTo("M9");
        assertThat(jwt.getClaimAsString("terminal_id")).isEqualTo("T9001");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("TERMINAL");
        assertThat(token.refreshToken()).isNull();
    }

    @Test
    void reusingARotatedRefreshTokenRevokesTheWholeSession() {
        admin.create("alice", "Alice@1234", AccountType.USER, Set.of(Role.AUDITOR), null, null);
        TokenResponse first = auth.login("alice", "Alice@1234");

        TokenResponse second = auth.refresh(first.refreshToken());      // rotation: first is now revoked

        assertThatThrownBy(() -> auth.refresh(first.refreshToken()))   // attacker replays the old one
                .isInstanceOf(AuthFailedException.class)
                .hasMessageContaining("reuse");
        assertThatThrownBy(() -> auth.refresh(second.refreshToken()))  // legit token died with the family
                .isInstanceOf(AuthFailedException.class);
    }

    @Test
    void accountLocksAfterRepeatedFailures() {
        admin.create("bob", "Bob@12345", AccountType.USER, Set.of(Role.AUDITOR), null, null);

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> auth.login("bob", "wrong")).isInstanceOf(AuthFailedException.class);
        }
        // Correct password is now refused because the account is locked.
        assertThatThrownBy(() -> auth.login("bob", "Bob@12345"))
                .isInstanceOf(AuthFailedException.class)
                .hasMessageContaining("locked");

        admin.unlock("bob");
        assertThat(auth.login("bob", "Bob@12345").accessToken()).isNotBlank();
    }
}
