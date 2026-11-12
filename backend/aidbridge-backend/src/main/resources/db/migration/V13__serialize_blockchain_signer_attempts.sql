-- C07: fail closed on existing duplicate open attempts; never delete/rewrite signed history.
-- Operators must reconcile duplicates before applying this migration.
CREATE UNIQUE INDEX uq_blockchain_tx_one_open_signer
    ON blockchain_transactions (chain_id, LOWER(signer_address))
    WHERE signer_address IS NOT NULL AND status IN ('PREPARED', 'SUBMITTED');

CREATE UNIQUE INDEX uq_blockchain_tx_one_open_job
    ON blockchain_transactions (blockchain_job_id)
    WHERE status IN ('PREPARED', 'SUBMITTED');
