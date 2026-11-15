-- V16__add_delivery_ref.sql
-- deliveryRef (bytes32) becomes a first-class immutable column (D56 item 2, confirmed by Khoa 14/11/2026).
-- It stays duplicated in audit_events.payload_private for the worker; both values must be identical.
LOCK TABLE deliveries IN ACCESS EXCLUSIVE MODE;

ALTER TABLE deliveries ADD COLUMN delivery_ref BYTEA;

-- Backfill by COPYING the ref already frozen in the immutable audit payload. Never generate a new one,
-- otherwise the column and the audit snapshot would disagree. The immutability trigger blocks UPDATE, so it
-- is disabled only inside this transaction (PostgreSQL DDL is transactional: a failure rolls everything back).
ALTER TABLE deliveries DISABLE TRIGGER deliveries_immutable;

UPDATE deliveries d
SET delivery_ref = decode(substr(a.payload_private->>'deliveryRef', 3), 'hex')
FROM audit_events a
WHERE a.entity_type = 'DELIVERY'
  AND a.entity_id = d.id
  AND a.event_type = 'DELIVERY_RECORDED'
  AND d.outcome = 'DELIVERED';

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM deliveries WHERE outcome = 'DELIVERED' AND delivery_ref IS NULL) THEN
        RAISE EXCEPTION 'DELIVERED delivery without a recorded deliveryRef';
    END IF;
END;
$$;

ALTER TABLE deliveries ENABLE TRIGGER deliveries_immutable;

ALTER TABLE deliveries
    ADD CONSTRAINT uq_delivery_ref UNIQUE (delivery_ref),
    ADD CONSTRAINT chk_delivery_ref_shape CHECK (
        delivery_ref IS NULL
        OR (octet_length(delivery_ref) = 32 AND delivery_ref <> decode(repeat('00', 32), 'hex'))
    ),
    -- FAILED attempts never reach the chain (no job), so they carry no ref.
    ADD CONSTRAINT chk_delivery_ref_by_outcome CHECK (
        (outcome = 'DELIVERED') = (delivery_ref IS NOT NULL)
    );
