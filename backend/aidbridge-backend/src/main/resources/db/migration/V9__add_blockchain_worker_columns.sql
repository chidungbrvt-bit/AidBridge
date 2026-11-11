ALTER TABLE organizations
    ADD COLUMN public_ref BYTEA UNIQUE
        DEFAULT decode(replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', ''), 'hex'),
    ADD COLUMN relayer_address VARCHAR(42);

UPDATE organizations
SET public_ref = decode(replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', ''), 'hex')
WHERE public_ref IS NULL;

ALTER TABLE organizations
    ALTER COLUMN public_ref SET NOT NULL,
    ADD CONSTRAINT chk_organization_public_ref_len
        CHECK (octet_length(public_ref) = 32 AND public_ref <> decode(repeat('00', 32), 'hex')),
    ADD CONSTRAINT chk_organization_relayer_address
        CHECK (relayer_address IS NULL OR relayer_address ~ '^0x[0-9a-fA-F]{40}$');

ALTER TABLE aid_cases
    ADD COLUMN public_ref BYTEA UNIQUE
        DEFAULT decode(replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', ''), 'hex');

UPDATE aid_cases
SET public_ref = decode(replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', ''), 'hex')
WHERE public_ref IS NULL;

ALTER TABLE aid_cases
    ALTER COLUMN public_ref SET NOT NULL,
    ADD CONSTRAINT chk_aid_case_public_ref_len
        CHECK (octet_length(public_ref) = 32 AND public_ref <> decode(repeat('00', 32), 'hex'));

ALTER TABLE blockchain_jobs
    ADD COLUMN logical_event_id BYTEA UNIQUE
        DEFAULT decode(replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', ''), 'hex'),
    ADD COLUMN chain_sequence BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN schema_version SMALLINT NOT NULL DEFAULT 1,
    ADD COLUMN manifest_private JSONB,
    ADD COLUMN salt_ciphertext BYTEA,
    ADD COLUMN evidence_commitment BYTEA,
    ADD COLUMN lease_until TIMESTAMPTZ;

UPDATE blockchain_jobs
SET logical_event_id = decode(replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', ''), 'hex')
WHERE logical_event_id IS NULL;

ALTER TABLE blockchain_jobs
    ALTER COLUMN logical_event_id SET NOT NULL,
    ADD CONSTRAINT chk_blockchain_job_logical_event_id_len
        CHECK (octet_length(logical_event_id) = 32 AND logical_event_id <> decode(repeat('00', 32), 'hex')),
    ADD CONSTRAINT chk_blockchain_job_chain_sequence
        CHECK (chain_sequence >= 1),
    ADD CONSTRAINT chk_blockchain_job_schema_version
        CHECK (schema_version >= 1),
    ADD CONSTRAINT chk_blockchain_job_commitment_len
        CHECK (
            evidence_commitment IS NULL
            OR (octet_length(evidence_commitment) = 32 AND evidence_commitment <> decode(repeat('00', 32), 'hex'))
        );

ALTER TABLE blockchain_transactions
DROP CONSTRAINT chk_blockchain_tx_status;

ALTER TABLE blockchain_transactions
    ADD COLUMN chain_id BIGINT NOT NULL DEFAULT 43113,
    ADD COLUMN contract_address VARCHAR(42),
    ADD COLUMN signer_address VARCHAR(42),
    ADD COLUMN nonce BIGINT,
    ADD COLUMN signed_tx_ciphertext BYTEA,
    ADD COLUMN receipt_private JSONB,
    ADD COLUMN prepared_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD CONSTRAINT chk_blockchain_tx_status
        CHECK (status IN ('PREPARED', 'SUBMITTED', 'CONFIRMED', 'REVERTED')),
    ADD CONSTRAINT chk_blockchain_tx_chain_id
        CHECK (chain_id > 0),
    ADD CONSTRAINT chk_blockchain_tx_nonce
        CHECK (nonce IS NULL OR nonce >= 0),
    ADD CONSTRAINT chk_blockchain_tx_contract_address
        CHECK (contract_address IS NULL OR contract_address ~ '^0x[0-9a-fA-F]{40}$'),
    ADD CONSTRAINT chk_blockchain_tx_signer_address
        CHECK (signer_address IS NULL OR signer_address ~ '^0x[0-9a-fA-F]{40}$');

CREATE UNIQUE INDEX uq_blockchain_tx_chain_signer_nonce
    ON blockchain_transactions (chain_id, LOWER(signer_address), nonce)
    WHERE signer_address IS NOT NULL AND nonce IS NOT NULL;