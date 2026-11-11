ALTER TABLE campaigns
    ADD COLUMN organization_id UUID,
    ADD COLUMN public_summary VARCHAR(2000),
    ADD COLUMN closed_at TIMESTAMPTZ,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);

UPDATE campaigns c
SET organization_id = ac.organization_id,
    public_summary = COALESCE(NULLIF(BTRIM(c.description), ''), ac.public_summary, ac.title),
    title = LEFT(BTRIM(c.title), 160)
FROM aid_cases ac
WHERE ac.id = c.aid_case_id;

ALTER TABLE campaigns
    ALTER COLUMN organization_id SET NOT NULL,
ALTER COLUMN public_summary SET NOT NULL,
    ALTER COLUMN title TYPE VARCHAR(160);

ALTER TABLE campaigns DROP CONSTRAINT chk_campaign_status;

UPDATE campaigns
SET status = CASE
                 WHEN status = 'ACTIVE' THEN 'PUBLISHED'
                 WHEN status = 'COMPLETED' THEN 'FULFILLED'
                 WHEN status IN ('PAUSED', 'CANCELLED') THEN 'CLOSED'
                 ELSE status
    END;

ALTER TABLE campaigns
    ADD CONSTRAINT chk_campaign_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'FULFILLED', 'CLOSED')),
    ADD CONSTRAINT uq_campaign_org
        UNIQUE (id, organization_id),
    ADD CONSTRAINT fk_campaign_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id),
    ADD CONSTRAINT fk_campaign_case_org
        FOREIGN KEY (aid_case_id, organization_id) REFERENCES aid_cases(id, organization_id);

DROP INDEX IF EXISTS idx_campaigns_status;
CREATE INDEX idx_campaigns_status_published
    ON campaigns (status, published_at, created_at, id);
CREATE INDEX idx_campaigns_org_status
    ON campaigns (organization_id, status, created_at, id);

ALTER TABLE campaign_items
    ADD COLUMN organization_id UUID,
    ADD COLUMN item_name VARCHAR(120),
    ADD COLUMN unit VARCHAR(40);

UPDATE campaign_items ci
SET organization_id = c.organization_id,
    item_name = n.item_name,
    unit = n.unit
    FROM campaigns c,
     case_need_items n
WHERE c.id = ci.campaign_id
  AND n.id = ci.case_need_item_id;

ALTER TABLE campaign_items
    ALTER COLUMN organization_id SET NOT NULL,
ALTER COLUMN item_name SET NOT NULL,
    ALTER COLUMN unit SET NOT NULL;

ALTER TABLE campaign_items DROP CONSTRAINT chk_campaign_target_qty;

ALTER TABLE campaign_items
    ADD CONSTRAINT chk_campaign_target_qty
        CHECK (target_quantity BETWEEN 1 AND 1000000),
    ADD CONSTRAINT fk_campaign_item_campaign_org
        FOREIGN KEY (campaign_id, organization_id) REFERENCES campaigns(id, organization_id);

CREATE UNIQUE INDEX uq_campaign_item_name_unit
    ON campaign_items (campaign_id, LOWER(item_name), LOWER(unit));