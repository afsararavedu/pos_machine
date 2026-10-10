package com.aifsar.pos.auth.service;

import java.time.Instant;
import java.util.UUID;

import com.aifsar.pos.auth.config.AuthProperties;
import com.aifsar.pos.auth.domain.RefreshToken;
import com.aifsar.pos.auth.domain.RefreshTokenRepository;
import com.aifsar.pos.auth.domain.UserAccount;
import com.aifsar.pos.auth.domain.UserAccount.AccountType;
import com.aifsar.pos.auth.domain.UserAccountRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication features:
 *  - user login (password)          -> short-lived access token + rotating refresh token
 *  - terminal login (client creds)  -> access token carrying merchant_id / terminal_id
 *  - refresh with rotation and reuse detection (replayed old token revokes the session)
 *  - logout (revokes the refresh-token family)
 *  - brute-force protection: account locks after N failed attempts
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String INVALID = "Invalid credentials";

    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final AuthProperties props;
    /** Used to spend the same time on unknown usernames, so response time does not reveal which exist. */
    private final String dummyHash;

    public AuthService(UserAccountRepository users, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, TokenService tokens, AuthProperties props) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.props = props;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(noRollbackFor = AuthFailedException.class)
    public TokenResponse login(String username, String password) {
        UserAccount account = authenticate(username, password, AccountType.USER);
        return issue(account, UUID.randomUUID());
    }

    @Transactional(noRollbackFor = AuthFailedException.class)
    public TokenResponse terminalToken(String terminalId, String secret) {
        UserAccount terminal = authenticate(terminalId, secret, AccountType.TERMINAL);
        long ttl = props.tokens().terminalTokenTtl().toSeconds();
        return new TokenResponse(tokens.accessToken(terminal, props.tokens().terminalTokenTtl()),
                "Bearer", ttl, null, terminal.roleList());
    }

    @Transactional(noRollbackFor = AuthFailedException.class)
    public TokenResponse refresh(String rawRefreshToken) {
        RefreshToken current = refreshTokens.findForUpdate(TokenService.sha256(rawRefreshToken))
                .orElseThrow(() -> new AuthFailedException("Invalid refresh token"));

        if (current.isRevoked()) {
            // A token that was already rotated is being used again: assume it was stolen.
            int revoked = refreshTokens.revokeFamily(current.getFamilyId(), "REUSE_DETECTED");
            log.warn("Refresh token reuse detected for user {}; revoked {} token(s) in family {}",
                    current.getUserId(), revoked, current.getFamilyId());
            throw new AuthFailedException("Refresh token reuse detected; please log in again");
        }
        Instant now = Instant.now();
        if (current.isExpired(now)) {
            current.revoke("EXPIRED");
            throw new AuthFailedException("Refresh token expired");
        }
        UserAccount account = users.findById(current.getUserId())
                .orElseThrow(() -> new AuthFailedException("Invalid refresh token"));
        if (!account.isEnabled() || account.isLocked(now)) {
            refreshTokens.revokeFamily(current.getFamilyId(), "ACCOUNT_INACTIVE");
            throw new AuthFailedException("Account is disabled or locked");
        }
        current.revoke("ROTATED");
        return issue(account, current.getFamilyId());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.findForUpdate(TokenService.sha256(rawRefreshToken))
                .ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId(), "LOGOUT"));
    }

    private UserAccount authenticate(String username, String secret, AccountType expectedType) {
        Instant now = Instant.now();
        UserAccount account = users.findByUsername(username).orElse(null);
        if (account == null || account.getAccountType() != expectedType) {
            passwordEncoder.matches(secret, dummyHash);
            throw new AuthFailedException(INVALID);
        }
        if (!account.isEnabled()) {
            throw new AuthFailedException(INVALID);
        }
        if (account.isLocked(now)) {
            throw new AuthFailedException("Account locked until " + account.getLockedUntil());
        }
        if (!passwordEncoder.matches(secret, account.getPasswordHash())) {
            account.registerFailure(props.lockout().maxFailedAttempts(), now.plus(props.lockout().lockDuration()));
            log.info("Failed login for {} (attempt {})", username, account.getFailedAttempts());
            throw new AuthFailedException(account.isLocked(now)
                    ? "Too many failed attempts; account locked" : INVALID);
        }
        account.registerSuccess();
        return account;
    }

    private TokenResponse issue(UserAccount account, UUID familyId) {
        Instant now = Instant.now();
        String refresh = tokens.newRefreshTokenValue();
        refreshTokens.save(new RefreshToken(TokenService.sha256(refresh), account.getId(), familyId, now,
                now.plus(props.tokens().refreshTokenTtl())));
        return new TokenResponse(tokens.accessToken(account, props.tokens().accessTokenTtl()), "Bearer",
                props.tokens().accessTokenTtl().toSeconds(), refresh, account.roleList());
    }
}
