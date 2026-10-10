package com.aifsar.pos.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Opaque refresh token. Only its SHA-256 hash is stored. Tokens issued from the same
 * login share a familyId: if an already-rotated token is presented again (a stolen
 * token being replayed), the whole family is revoked.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID familyId;

    @Column(nullable = false)
    private Instant issuedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private boolean revoked;
    private String revokedReason;

    protected RefreshToken() {
    }

    public RefreshToken(String tokenHash, UUID userId, UUID familyId, Instant issuedAt, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.familyId = familyId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public void revoke(String reason) {
        this.revoked = true;
        this.revokedReason = reason;
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getFamilyId() { return familyId; }
    public boolean isRevoked() { return revoked; }
    public String getRevokedReason() { return revokedReason; }
}
