package com.aifsar.pos.auth.domain;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** A human user (admin, auditor, merchant staff) or a POS terminal (client-credentials). */
@Entity
@Table(name = "user_accounts")
public class UserAccount {

    public enum AccountType { USER, TERMINAL }

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType accountType;

    /** Comma-separated {@link Role} names. */
    @Column(nullable = false)
    private String roles;

    private String merchantId;
    private String terminalId;
    private boolean enabled;
    private int failedAttempts;
    private Instant lockedUntil;

    @Column(nullable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected UserAccount() {
    }

    public UserAccount(String username, String passwordHash, AccountType accountType, Collection<Role> roles,
                       String merchantId, String terminalId) {
        this.id = UUID.randomUUID();
        this.username = username;
        this.passwordHash = passwordHash;
        this.accountType = accountType;
        this.roles = String.join(",", roles.stream().map(Role::name).toList());
        this.merchantId = merchantId;
        this.terminalId = terminalId;
        this.enabled = true;
        this.createdAt = Instant.now();
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Records a failed attempt and locks the account once the threshold is reached. */
    public void registerFailure(int maxAttempts, Instant lockUntil) {
        failedAttempts++;
        if (failedAttempts >= maxAttempts) {
            lockedUntil = lockUntil;
            failedAttempts = 0;
        }
    }

    public void registerSuccess() {
        failedAttempts = 0;
        lockedUntil = null;
    }

    public void unlock() {
        registerSuccess();
    }

    public void disable() {
        enabled = false;
    }

    public List<String> roleList() {
        return Arrays.stream(roles.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public AccountType getAccountType() { return accountType; }
    public String getMerchantId() { return merchantId; }
    public String getTerminalId() { return terminalId; }
    public boolean isEnabled() { return enabled; }
    public int getFailedAttempts() { return failedAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
}
