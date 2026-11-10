CREATE TABLE campaigns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aid_case_id UUID NOT NULL UNIQUE REFERENCES aid_cases(id),
    created_by UUID NOT NULL REFERENCES users(id),
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_campaign_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_campaigns_status
    ON campaigns (status);

CREATE TABLE campaign_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID NOT NULL REFERENCES campaigns(id),
    case_need_item_id UUID NOT NULL REFERENCES case_need_items(id),
    target_quantity INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_campaign_need_item UNIQUE (campaign_id, case_need_item_id),
    CONSTRAINT chk_campaign_target_qty CHECK (target_quantity > 0)
);

CREATE INDEX idx_campaign_items_campaign
    ON campaign_items (campaign_id);

CREATE TABLE contributions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID NOT NULL REFERENCES campaigns(id),
    donor_user_id UUID NOT NULL REFERENCES users(id),
    idempotency_key UUID NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL DEFAULT 'PLEDGED',
    donor_note TEXT,
    pledged_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_contribution_status
        CHECK (status IN (
            'PLEDGED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED'
        ))
);

CREATE INDEX idx_contributions_campaign_status
    ON contributions (campaign_id, status);
CREATE INDEX idx_contributions_donor
    ON contributions (donor_user_id);

CREATE TABLE contribution_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contribution_id UUID NOT NULL REFERENCES contributions(id),
    campaign_item_id UUID NOT NULL REFERENCES campaign_items(id),
    pledged_quantity INTEGER NOT NULL,

    CONSTRAINT uq_contribution_campaign_item
        UNIQUE (contribution_id, campaign_item_id),
    CONSTRAINT chk_contribution_item_qty CHECK (pledged_quantity > 0)
);

CREATE INDEX idx_contribution_items_campaign_item
    ON contribution_items (campaign_item_id);

CREATE TABLE goods_receipts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contribution_id UUID NOT NULL REFERENCES contributions(id),
    received_by UUID NOT NULL REFERENCES users(id),
    idempotency_key UUID NOT NULL UNIQUE,
    receipt_note TEXT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_goods_receipts_contribution
    ON goods_receipts (contribution_id);

CREATE TABLE goods_receipt_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    goods_receipt_id UUID NOT NULL REFERENCES goods_receipts(id),
    contribution_item_id UUID NOT NULL REFERENCES contribution_items(id),
    received_quantity INTEGER NOT NULL,

    CONSTRAINT uq_goods_receipt_item
        UNIQUE (goods_receipt_id, contribution_item_id),
    CONSTRAINT chk_goods_receipt_qty CHECK (received_quantity > 0)
);

CREATE INDEX idx_goods_receipt_items_contribution_item
    ON goods_receipt_items (contribution_item_id);
