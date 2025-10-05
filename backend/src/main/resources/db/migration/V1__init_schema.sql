CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    amount NUMERIC(19, 2) NOT NULL,
    category VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    notes TEXT,
    source_type VARCHAR(32) NOT NULL,
    receipt_reference VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transactions_occurred_at ON transactions (occurred_at);
CREATE INDEX idx_transactions_category ON transactions (category);

CREATE TABLE receipt_ingestions (
    id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(128) NOT NULL UNIQUE,
    receipt_uri TEXT,
    status VARCHAR(32) NOT NULL,
    transaction_id BIGINT,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_receipt_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id)
);

CREATE TABLE savings_snapshots (
    id BIGSERIAL PRIMARY KEY,
    amount NUMERIC(19, 2) NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_savings_snapshots_captured_at ON savings_snapshots (captured_at);
