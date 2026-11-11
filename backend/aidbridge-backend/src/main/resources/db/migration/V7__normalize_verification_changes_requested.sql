ALTER TABLE verifications DROP CONSTRAINT chk_verification_decision;

UPDATE verifications
SET decision = 'CHANGES_REQUESTED'
WHERE decision = 'NEEDS_INFO';

ALTER TABLE verifications ADD CONSTRAINT chk_verification_decision
    CHECK (decision IN ('APPROVED', 'REJECTED', 'CHANGES_REQUESTED'));
