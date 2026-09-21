CREATE TABLE purchases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id UUID NOT NULL REFERENCES reports(id),
    buyer_id UUID NOT NULL REFERENCES users(id),
    amount_byn BIGINT NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending',
    bepaid_checkout_token TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    paid_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX  purchases_report_id_buyer_id_idx ON purchases (report_id, buyer_id)
WHERE status IN ('pending', 'paid');

CREATE TABLE idempotency_keys (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE report_access_tokens(
    id BIGSERIAL PRIMARY KEY,
    purchases_id UUID NOT NULL REFERENCES purchases(id),
    token_hash TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE payout_batches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inspector_id UUID NOT NULL REFERENCES inspectors(id),
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    amount_byn BIGINT NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    paid_at TIMESTAMPTZ
);

CREATE TABLE inspector_payouts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_id UUID NOT NULL REFERENCES purchases(id),
    inspector_id UUID NOT NULL REFERENCES inspectors(id),
    amount_byn BIGINT NOT NULL,
    payout_batch_id UUID REFERENCES payout_batches(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);