-- V15__reconcile_delivery_schema.sql
LOCK TABLE deliveries IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM deliveries) THEN
        RAISE EXCEPTION 'Legacy deliveries require explicit reconciliation before V15';
END IF;
END;
$$;

ALTER TABLE campaigns
    ADD CONSTRAINT uq_campaign_id_case UNIQUE (id, aid_case_id);

ALTER TABLE deliveries
DROP CONSTRAINT chk_delivery_status,
    ALTER COLUMN status DROP DEFAULT,
    ALTER COLUMN idempotency_key DROP NOT NULL,
    ALTER COLUMN delivered_at DROP DEFAULT,
    ALTER COLUMN delivered_at DROP NOT NULL,
    ADD COLUMN campaign_id UUID NOT NULL,
    ADD CONSTRAINT uq_delivery_id_campaign UNIQUE (id, campaign_id),
    ADD CONSTRAINT uq_delivery_id_case UNIQUE (id, aid_case_id),
    ADD CONSTRAINT fk_delivery_campaign_case
        FOREIGN KEY (campaign_id, aid_case_id)
        REFERENCES campaigns (id, aid_case_id);

ALTER TABLE deliveries RENAME COLUMN status TO outcome;

ALTER TABLE deliveries
    ADD CONSTRAINT chk_delivery_outcome CHECK (outcome IN ('DELIVERED', 'FAILED')),
    ADD CONSTRAINT chk_failed_delivery_reason CHECK (
        outcome <> 'FAILED'
        OR (
            handover_note IS NOT NULL
            AND char_length(btrim(handover_note)) BETWEEN 1 AND 2000
        )
    );

ALTER TABLE delivery_items
    ADD COLUMN campaign_id UUID NOT NULL,
    ADD CONSTRAINT fk_delivery_item_delivery_campaign
        FOREIGN KEY (delivery_id, campaign_id)
        REFERENCES deliveries (id, campaign_id),
    ADD CONSTRAINT fk_delivery_item_campaign_item
        FOREIGN KEY (campaign_item_id, campaign_id)
        REFERENCES campaign_items (id, campaign_id);

ALTER TABLE delivery_evidence_links
    ADD COLUMN aid_case_id UUID NOT NULL,
    ADD COLUMN organization_id UUID NOT NULL,
    ADD CONSTRAINT fk_delivery_evidence_delivery_case
        FOREIGN KEY (delivery_id, aid_case_id)
        REFERENCES deliveries (id, aid_case_id),
    ADD CONSTRAINT fk_delivery_evidence_case_org
        FOREIGN KEY (aid_case_id, organization_id)
        REFERENCES aid_cases (id, organization_id),
    ADD CONSTRAINT fk_delivery_evidence_version_owner
        FOREIGN KEY (evidence_version_id, aid_case_id, organization_id)
        REFERENCES evidence_versions (id, aid_case_id, organization_id);

CREATE TRIGGER deliveries_immutable
    BEFORE UPDATE OR DELETE ON deliveries
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_fact_change();

CREATE TRIGGER delivery_items_immutable
    BEFORE UPDATE OR DELETE ON delivery_items
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_fact_change();

CREATE TRIGGER delivery_evidence_links_immutable
    BEFORE UPDATE OR DELETE ON delivery_evidence_links
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_fact_change();