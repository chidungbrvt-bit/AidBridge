-- Record delivery invalidation without rewriting historical delivery facts.

ALTER TABLE disputes
    ADD CONSTRAINT uq_dispute_id_delivery_case
        UNIQUE (id, delivery_id, aid_case_id);

CREATE TABLE delivery_voids (
                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                delivery_id UUID NOT NULL,
                                aid_case_id UUID NOT NULL,
                                dispute_id UUID NOT NULL,
                                voided_by UUID NOT NULL REFERENCES users(id),
                                reason TEXT NOT NULL,
                                voided_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                                CONSTRAINT uq_delivery_void_delivery
                                    UNIQUE (delivery_id),

                                CONSTRAINT uq_delivery_void_dispute
                                    UNIQUE (dispute_id),

                                CONSTRAINT fk_delivery_void_delivery_case
                                    FOREIGN KEY (delivery_id, aid_case_id)
                                        REFERENCES deliveries (id, aid_case_id),

                                CONSTRAINT fk_delivery_void_dispute_delivery_case
                                    FOREIGN KEY (dispute_id, delivery_id, aid_case_id)
                                        REFERENCES disputes (id, delivery_id, aid_case_id),

                                CONSTRAINT chk_delivery_void_reason
                                    CHECK (char_length(btrim(reason)) BETWEEN 1 AND 2000)
);

CREATE INDEX idx_delivery_voids_case
    ON delivery_voids (aid_case_id);

CREATE TRIGGER delivery_voids_immutable
    BEFORE UPDATE OR DELETE ON delivery_voids
    FOR EACH ROW
    EXECUTE FUNCTION reject_immutable_fact_change();