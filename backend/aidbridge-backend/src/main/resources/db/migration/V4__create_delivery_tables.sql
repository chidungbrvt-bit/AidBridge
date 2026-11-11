CREATE TABLE deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aid_case_id UUID NOT NULL REFERENCES aid_cases(id),
    recorded_by UUID NOT NULL REFERENCES users(id),
    idempotency_key UUID NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL DEFAULT 'RECORDED',
    handover_note TEXT,
    delivered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_delivery_status
        CHECK (status IN ('RECORDED', 'CONFIRMED', 'DISPUTED', 'CANCELLED'))
);

CREATE INDEX idx_deliveries_case_status
    ON deliveries (aid_case_id, status);

CREATE TABLE delivery_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_id UUID NOT NULL REFERENCES deliveries(id),
    campaign_item_id UUID NOT NULL REFERENCES campaign_items(id),
    delivered_quantity INTEGER NOT NULL,

    CONSTRAINT uq_delivery_campaign_item
        UNIQUE (delivery_id, campaign_item_id),
    CONSTRAINT chk_delivery_item_qty CHECK (delivered_quantity > 0)
);

CREATE INDEX idx_delivery_items_campaign_item
    ON delivery_items (campaign_item_id);

CREATE TABLE delivery_confirmations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_id UUID NOT NULL REFERENCES deliveries(id),
    confirmed_by UUID NOT NULL REFERENCES users(id),
    decision VARCHAR(20) NOT NULL,
    notes TEXT,
    confirmed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_delivery_confirmation_decision
        CHECK (decision IN ('CONFIRMED', 'REJECTED', 'NEEDS_REVIEW'))
);

CREATE INDEX idx_delivery_confirmations_delivery_time
    ON delivery_confirmations (delivery_id, confirmed_at DESC);

-- A delivery can have several review attempts but at most one successful confirmation.
CREATE UNIQUE INDEX uq_delivery_successful_confirmation
    ON delivery_confirmations (delivery_id)
    WHERE decision = 'CONFIRMED';
