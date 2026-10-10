CREATE TABLE user_accounts (
    id              UUID PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    account_type    VARCHAR(16)  NOT NULL,
    roles           VARCHAR(255) NOT NULL,
    merchant_id     VARCHAR(32),
    terminal_id     VARCHAR(32),
    enabled         BOOLEAN      NOT NULL,
    failed_attempts INT          NOT NULL,
    locked_until    TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    version         BIGINT       NOT NULL
);

CREATE TABLE refresh_tokens (
    id             UUID PRIMARY KEY,
    token_hash     VARCHAR(64) NOT NULL UNIQUE,
    user_id        UUID        NOT NULL REFERENCES user_accounts (id),
    family_id      UUID        NOT NULL,
    issued_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked        BOOLEAN     NOT NULL,
    revoked_reason VARCHAR(32)
);

CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
