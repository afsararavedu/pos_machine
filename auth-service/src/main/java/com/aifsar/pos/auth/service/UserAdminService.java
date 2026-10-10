package com.aifsar.pos.auth.service;

import java.util.Set;

import com.aifsar.pos.auth.domain.RefreshTokenRepository;
import com.aifsar.pos.auth.domain.Role;
import com.aifsar.pos.auth.domain.UserAccount;
import com.aifsar.pos.auth.domain.UserAccount.AccountType;
import com.aifsar.pos.auth.domain.UserAccountRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdminService {

    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;

    public UserAdminService(UserAccountRepository users, RefreshTokenRepository refreshTokens,
                            PasswordEncoder passwordEncoder) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserAccount create(String username, String rawSecret, AccountType type, Set<Role> roles,
                              String merchantId, String terminalId) {
        if (users.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
        if (type == AccountType.TERMINAL && (terminalId == null || merchantId == null)) {
            throw new IllegalArgumentException("A terminal needs both merchantId and terminalId");
        }
        if (roles.contains(Role.MERCHANT) && merchantId == null) {
            throw new IllegalArgumentException("A MERCHANT user needs a merchantId");
        }
        return users.save(new UserAccount(username, passwordEncoder.encode(rawSecret), type, roles,
                merchantId, terminalId));
    }

    @Transactional
    public void unlock(String username) {
        find(username).unlock();
    }

    /** Disabling also kills every outstanding refresh token so existing sessions cannot be extended. */
    @Transactional
    public void disable(String username) {
        UserAccount account = find(username);
        account.disable();
        refreshTokens.revokeAllForUser(account.getId(), "ACCOUNT_DISABLED");
    }

    private UserAccount find(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user: " + username));
    }
}
