CREATE TABLE transaction_view (
    payment_id         UUID PRIMARY KEY,
    merchant_id        VARCHAR(32)  NOT NULL,
    terminal_id        VARCHAR(32)  NOT NULL,
    stan               VARCHAR(6),
    rrn                VARCHAR(12),
    masked_pan         VARCHAR(19),
    entry_mode         VARCHAR(16),
    amount_minor       BIGINT       NOT NULL,
    currency           VARCHAR(3)   NOT NULL,
    status             VARCHAR(32)  NOT NULL,
    response_code      VARCHAR(4),
    response_message   VARCHAR(160),
    auth_code          VARCHAR(6),
    duplicate_attempts INT          NOT NULL,
    last_sequence      BIGINT       NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_view_merchant_created ON transaction_view (merchant_id, created_at);
CREATE INDEX idx_view_status_created ON transaction_view (status, created_at);

-- Audit log AND idempotent-consumer ledger (PK = eventId).
CREATE TABLE transaction_events (
    event_id         UUID PRIMARY KEY,
    payment_id       UUID         NOT NULL,
    event_type       VARCHAR(64)  NOT NULL,
    status           VARCHAR(32)  NOT NULL,
    response_code    VARCHAR(4),
    response_message VARCHAR(160),
    event_sequence   BIGINT       NOT NULL,
    occurred_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_events_payment ON transaction_events (payment_id, event_sequence);
