CREATE TABLE evidence_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    uploaded_by UUID NOT NULL REFERENCES users(id),
    title VARCHAR(200) NOT NULL,
    access_level VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_evidence_access
        CHECK (access_level IN ('PRIVATE', 'ORGANIZATION', 'PUBLIC'))
);

CREATE INDEX idx_evidence_files_organization
    ON evidence_files (organization_id);

CREATE TABLE evidence_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evidence_file_id UUID NOT NULL REFERENCES evidence_files(id),
    version_number INTEGER NOT NULL,
    storage_key TEXT NOT NULL UNIQUE,
    sha256_hex CHAR(64) NOT NULL,
    mime_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL,
    uploaded_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_evidence_version UNIQUE (evidence_file_id, version_number),
    CONSTRAINT chk_evidence_version_positive CHECK (version_number > 0),
    CONSTRAINT chk_evidence_size CHECK (size_bytes >= 0),
    CONSTRAINT chk_evidence_sha256
        CHECK (sha256_hex ~ '^[0-9a-fA-F]{64}$')
);

-- A link must point to a specific version, not a mutable file pointer.
CREATE TABLE case_evidence_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aid_case_id UUID NOT NULL REFERENCES aid_cases(id),
    evidence_version_id UUID NOT NULL REFERENCES evidence_versions(id),
    purpose VARCHAR(40) NOT NULL DEFAULT 'SUPPORTING_DOCUMENT',
    linked_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_case_evidence_link
        UNIQUE (aid_case_id, evidence_version_id, purpose)
);

CREATE INDEX idx_case_evidence_links_case
    ON case_evidence_links (aid_case_id);

CREATE TABLE delivery_evidence_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_id UUID NOT NULL REFERENCES deliveries(id),
    evidence_version_id UUID NOT NULL REFERENCES evidence_versions(id),
    purpose VARCHAR(40) NOT NULL DEFAULT 'HANDOVER_PROOF',
    linked_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_delivery_evidence_link
        UNIQUE (delivery_id, evidence_version_id, purpose)
);

CREATE INDEX idx_delivery_evidence_links_delivery
    ON delivery_evidence_links (delivery_id);

CREATE TABLE consent_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    beneficiary_id UUID NOT NULL REFERENCES beneficiaries(id),
    recorded_by UUID NOT NULL REFERENCES users(id),
    consent_scope VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    evidence_version_id UUID REFERENCES evidence_versions(id),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    notes TEXT,

    CONSTRAINT chk_consent_status
        CHECK (status IN ('GRANTED', 'WITHDRAWN'))
);

CREATE INDEX idx_consent_records_beneficiary_time
    ON consent_records (beneficiary_id, recorded_at DESC);

CREATE TABLE disputes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aid_case_id UUID NOT NULL REFERENCES aid_cases(id),
    delivery_id UUID REFERENCES deliveries(id),
    opened_by UUID NOT NULL REFERENCES users(id),
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    reason TEXT NOT NULL,
    resolution_note TEXT,
    resolved_by UUID REFERENCES users(id),
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_dispute_status
        CHECK (status IN ('OPEN', 'UNDER_REVIEW', 'RESOLVED', 'REJECTED'))
);

CREATE INDEX idx_disputes_case_status
    ON disputes (aid_case_id, status);

-- Audit events are append-only business events, written in the same DB transaction
-- as the corresponding case / verification / receipt / delivery change.
CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID REFERENCES organizations(id),
    aid_case_id UUID REFERENCES aid_cases(id),
    actor_user_id UUID REFERENCES users(id),
    entity_type VARCHAR(60) NOT NULL,
    entity_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    event_key UUID NOT NULL UNIQUE,
    public_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    evidence_commitment CHAR(64),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_audit_evidence_commitment
        CHECK (
            evidence_commitment IS NULL
            OR evidence_commitment ~ '^[0-9a-fA-F]{64}$'
        )
);

CREATE INDEX idx_audit_events_case_time
    ON audit_events (aid_case_id, occurred_at DESC);
CREATE INDEX idx_audit_events_org_time
    ON audit_events (organization_id, occurred_at DESC);

-- Transactional outbox: a scheduled worker will send the audit event to Fuji.
CREATE TABLE blockchain_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    audit_event_id UUID NOT NULL UNIQUE REFERENCES audit_events(id),
    chain_id BIGINT NOT NULL DEFAULT 43113,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_blockchain_job_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'SUBMITTED', 'CONFIRMED', 'FAILED')),
    CONSTRAINT chk_blockchain_job_retry CHECK (retry_count >= 0),
    CONSTRAINT chk_blockchain_job_chain CHECK (chain_id > 0)
);

CREATE INDEX idx_blockchain_jobs_pending
    ON blockchain_jobs (status, next_retry_at);

-- Multiple transaction attempts can belong to a single job.
CREATE TABLE blockchain_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    blockchain_job_id UUID NOT NULL REFERENCES blockchain_jobs(id),
    tx_hash VARCHAR(66) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    block_number BIGINT,
    receipt_status SMALLINT,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    confirmed_at TIMESTAMPTZ,
    error_message TEXT,

    CONSTRAINT chk_blockchain_tx_status
        CHECK (status IN ('SUBMITTED', 'CONFIRMED', 'REVERTED')),
    CONSTRAINT chk_blockchain_tx_hash
        CHECK (tx_hash ~ '^0x[0-9a-fA-F]{64}$'),
    CONSTRAINT chk_blockchain_receipt_status
        CHECK (receipt_status IS NULL OR receipt_status IN (0, 1)),
    CONSTRAINT chk_blockchain_block_number
        CHECK (block_number IS NULL OR block_number >= 0)
);

CREATE INDEX idx_blockchain_transactions_job
    ON blockchain_transactions (blockchain_job_id);
