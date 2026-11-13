DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM goods_receipt_items gri
        JOIN goods_receipts gr ON gr.id = gri.goods_receipt_id
        JOIN contribution_items ci ON ci.id = gri.contribution_item_id
        WHERE gr.contribution_id <> ci.contribution_id
    ) THEN
        RAISE EXCEPTION 'Legacy receipt lines must belong to the receipt commitment';
    END IF;

    IF EXISTS (
        SELECT 1 FROM goods_receipt_items gri
        JOIN contribution_items ci ON ci.id = gri.contribution_item_id
        GROUP BY ci.id
        HAVING SUM(gri.received_quantity) > ci.pledged_quantity - ci.cancelled_quantity
    ) THEN
        RAISE EXCEPTION 'Legacy receipts exceed the active commitment quantity';
    END IF;
END;
$$;

ALTER TABLE goods_receipts
    ALTER COLUMN idempotency_key DROP NOT NULL,
    ADD CONSTRAINT uq_goods_receipt_commitment UNIQUE (id, contribution_id);

ALTER TABLE contribution_items
    ADD CONSTRAINT uq_contribution_item_commitment UNIQUE (id, contribution_id);

ALTER TABLE goods_receipt_items ADD COLUMN contribution_id UUID;

UPDATE goods_receipt_items gri
SET contribution_id = gr.contribution_id
FROM goods_receipts gr
WHERE gr.id = gri.goods_receipt_id;

ALTER TABLE goods_receipt_items
    ALTER COLUMN contribution_id SET NOT NULL,
    ADD CONSTRAINT fk_receipt_item_receipt_commitment
        FOREIGN KEY (goods_receipt_id, contribution_id) REFERENCES goods_receipts (id, contribution_id),
    ADD CONSTRAINT fk_receipt_item_contribution_commitment
        FOREIGN KEY (contribution_item_id, contribution_id) REFERENCES contribution_items (id, contribution_id);

CREATE TRIGGER goods_receipts_immutable BEFORE UPDATE OR DELETE ON goods_receipts
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_fact_change();

CREATE TRIGGER goods_receipt_items_immutable BEFORE UPDATE OR DELETE ON goods_receipt_items
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_fact_change();
