-- Users
CREATE TABLE users (
    id              UUID PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    password        VARCHAR(255) NOT NULL,
    type            VARCHAR(20) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    document        VARCHAR(255) NOT NULL,
    document_hash   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_document_hash UNIQUE (document_hash)
);
-- Wallets
CREATE TABLE wallets (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL,
    balance         NUMERIC(19,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_wallets_user_id UNIQUE (user_id)
);
-- Transfers
CREATE TABLE transfers (
    id                      UUID PRIMARY KEY,
    amount                  NUMERIC(19,2) NOT NULL,
    description             VARCHAR(500),
    status                  VARCHAR(20) NOT NULL,
    source_wallet_id        UUID NOT NULL,
    destination_wallet_id   UUID NOT NULL,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP,
    version                 BIGINT NOT NULL DEFAULT 0
);
-- Transactions (immutable ledger)
CREATE TABLE transactions (
    id              UUID PRIMARY KEY,
    wallet_id       UUID NOT NULL,
    transfer_id     UUID NOT NULL,
    type            VARCHAR(20) NOT NULL,
    amount          NUMERIC(19,2) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP,
    version         BIGINT NOT NULL DEFAULT 0
);
-- Processed transfers (idempotency guard)
CREATE TABLE processed_transfers (
    id              UUID PRIMARY KEY,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP,
    version         BIGINT NOT NULL DEFAULT 0
);
-- Outbox (transactional outbox pattern)
CREATE TABLE outbox (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(255) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(255) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    processed       BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at    TIMESTAMP,
    attempts        INTEGER NOT NULL DEFAULT 0
);
-- Indexes
CREATE INDEX idx_outbox_processed_created ON outbox(processed, created_at);
CREATE INDEX idx_transfers_source_wallet ON transfers(source_wallet_id);
CREATE INDEX idx_transfers_destination_wallet ON transfers(destination_wallet_id);
CREATE INDEX idx_transfers_status ON transfers(status);
CREATE INDEX idx_transactions_wallet_id ON transactions(wallet_id);
CREATE INDEX idx_transactions_transfer_id ON transactions(transfer_id);