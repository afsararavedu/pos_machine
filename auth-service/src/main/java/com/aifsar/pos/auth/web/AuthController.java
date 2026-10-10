package com.aifsar.pos.auth.web;

import java.util.Map;

import com.aifsar.pos.auth.service.AuthService;
import com.aifsar.pos.auth.service.TokenResponse;
import com.aifsar.pos.common.security.CurrentPrincipal;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record TerminalTokenRequest(@NotBlank String terminalId, @NotBlank String secret) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    private final AuthService authService;
    private final Map<String, Object> publicJwks;

    public AuthController(AuthService authService, RSAKey signingKey) {
        this.authService = authService;
        this.publicJwks = new JWKSet(signingKey.toPublicJWK()).toJSONObject();
    }

    @PostMapping("/auth/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }

    /** OAuth2 client-credentials style login for POS terminals. */
    @PostMapping("/auth/terminal-token")
    public TokenResponse terminalToken(@Valid @RequestBody TerminalTokenRequest request) {
        return authService.terminalToken(request.terminalId(), request.secret());
    }

    @PostMapping("/auth/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/auth/me")
    public CurrentPrincipal me(@AuthenticationPrincipal Jwt jwt) {
        return CurrentPrincipal.from(jwt);
    }

    /** Public keys only. Every other service validates tokens against this endpoint. */
    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return publicJwks;
    }
}
