-- B07: reconcile V3 contribution tables with the B07 contract and add the donor grant (D51).

-- D51: explicit donor capability. FALSE by default so no existing account becomes a donor implicitly.
ALTER TABLE users
    ADD COLUMN donor_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- V3 rows have a global idempotency_key and no organization/public_ref. Reshaping them could
-- silently lose data, so stop instead. No pledge endpoint existed before B07, so this is expected to pass.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM contributions) THEN
        RAISE EXCEPTION 'V11 requires an empty contributions table. Reconcile legacy pledges before migrating.';
END IF;
END $$;

-- Target for composite FKs: lets a child row prove its campaign item belongs to the same campaign.
ALTER TABLE campaign_items
    ADD CONSTRAINT uq_campaign_item_campaign UNIQUE (id, campaign_id);

-- The request journal (idempotency_records, scoped by actor/operation/key) replaces the per-row key.
ALTER TABLE contributions
DROP COLUMN idempotency_key;

-- Safe without backfill: the guard above guarantees the table is empty.
ALTER TABLE contributions
    ADD COLUMN organization_id UUID NOT NULL,
    ADD COLUMN public_ref BYTEA NOT NULL
        DEFAULT decode(replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', ''), 'hex'),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE contributions
    ADD CONSTRAINT uq_contribution_public_ref UNIQUE (public_ref),
    ADD CONSTRAINT chk_contribution_public_ref_len
        CHECK (octet_length(public_ref) = 32 AND public_ref <> decode(repeat('00', 32), 'hex')),
    ADD CONSTRAINT chk_contribution_version CHECK (version >= 0),
    ADD CONSTRAINT uq_contribution_campaign UNIQUE (id, campaign_id),
    ADD CONSTRAINT fk_contribution_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id),
    ADD CONSTRAINT fk_contribution_campaign_org
        FOREIGN KEY (campaign_id, organization_id) REFERENCES campaigns(id, organization_id);

-- G02 history: own pledges, newest first, with a deterministic tie-breaker.
DROP INDEX IF EXISTS idx_contributions_donor;
CREATE INDEX idx_contributions_donor_pledged
    ON contributions (donor_user_id, pledged_at DESC, id);

-- Child table is empty too (FK to contributions), so NOT NULL needs no backfill.
ALTER TABLE contribution_items
    ADD COLUMN organization_id UUID NOT NULL,
    ADD COLUMN campaign_id UUID NOT NULL,
    ADD COLUMN cancelled_quantity INTEGER NOT NULL DEFAULT 0;

ALTER TABLE contribution_items
DROP CONSTRAINT chk_contribution_item_qty;

-- Defense in depth: the service validates first, but the database still rejects cross-campaign
-- or cross-organization rows and impossible cancellation counts if a bug slips through.
ALTER TABLE contribution_items
    ADD CONSTRAINT chk_contribution_item_qty
        CHECK (pledged_quantity BETWEEN 1 AND 1000000),
    ADD CONSTRAINT chk_contribution_item_cancelled
        CHECK (cancelled_quantity BETWEEN 0 AND pledged_quantity),
    ADD CONSTRAINT fk_contribution_item_contribution_campaign
        FOREIGN KEY (contribution_id, campaign_id) REFERENCES contributions(id, campaign_id),
    ADD CONSTRAINT fk_contribution_item_campaign_item_campaign
        FOREIGN KEY (campaign_item_id, campaign_id) REFERENCES campaign_items(id, campaign_id),
    ADD CONSTRAINT fk_contribution_item_campaign_org
        FOREIGN KEY (campaign_id, organization_id) REFERENCES campaigns(id, organization_id);

-- goods_receipts / goods_receipt_items are intentionally untouched (B08).