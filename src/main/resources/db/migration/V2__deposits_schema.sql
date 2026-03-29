CREATE TABLE deposits (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL,
    wallet_id               UUID NOT NULL,
    external_payment_reference VARCHAR(255) NOT NULL,
    amount                  DECIMAL(19, 4) NOT NULL CHECK (amount > 0),
    status                  VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    payment_provider        VARCHAR(20)  NOT NULL,
    version                 BIGINT       NOT NULL DEFAULT 0,
    created_at              TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP    NOT NULL DEFAULT now(),
    provider_response       JSONB,

    UNIQUE(payment_provider, external_payment_reference),
    CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'CANCELED'))
);

CREATE INDEX idx_deposits_user_id   ON deposits(user_id);
CREATE INDEX idx_deposits_wallet_id ON deposits(wallet_id);
