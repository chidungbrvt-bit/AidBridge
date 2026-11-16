-- Preserve historical decisions and support the D02 decision vocabulary.
-- Do not rewrite previously applied migrations.

LOCK TABLE delivery_confirmations IN ACCESS EXCLUSIVE MODE;

ALTER TABLE delivery_confirmations
DROP CONSTRAINT chk_delivery_confirmation_decision;

ALTER TABLE delivery_confirmations
    ADD CONSTRAINT chk_delivery_confirmation_decision
        CHECK (
            decision IN (
                         'CONFIRMED',
                         'REJECTED',
                         'NEEDS_REVIEW',
                         'REQUIRES_REVIEW'
                )
            ),
    ADD CONSTRAINT chk_delivery_confirmation_review_reason
        CHECK (
            decision <> 'REQUIRES_REVIEW'
            OR (
                notes IS NOT NULL
                AND char_length(btrim(notes)) BETWEEN 1 AND 2000
            )
        );

-- Review decisions are historical facts.
-- Corrections must create new business records instead of rewriting history.
CREATE TRIGGER delivery_confirmations_immutable
    BEFORE UPDATE OR DELETE ON delivery_confirmations
    FOR EACH ROW
    EXECUTE FUNCTION reject_immutable_fact_change();