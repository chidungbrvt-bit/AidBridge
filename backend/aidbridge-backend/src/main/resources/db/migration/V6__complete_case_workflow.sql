-- D41: forward-only B04 reconciliation. Never fabricate consent for legacy cases.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM aid_cases WHERE priority IN ('LOW', 'HIGH')) THEN
        RAISE EXCEPTION 'Legacy case priorities require an explicit mapping before V6';
    END IF;
    IF EXISTS (
        SELECT 1 FROM (
            SELECT aid_case_id,evidence_version_id FROM case_evidence_links
            UNION ALL
            SELECT d.aid_case_id,l.evidence_version_id FROM delivery_evidence_links l JOIN deliveries d ON d.id=l.delivery_id
        ) l
        JOIN evidence_versions v ON v.id = l.evidence_version_id
        JOIN evidence_files f ON f.id = v.evidence_file_id
        JOIN aid_cases c ON c.id = l.aid_case_id
        WHERE c.organization_id <> f.organization_id
    ) OR EXISTS (
        SELECT 1 FROM (
            SELECT aid_case_id,evidence_version_id FROM case_evidence_links
            UNION ALL
            SELECT d.aid_case_id,l.evidence_version_id FROM delivery_evidence_links l JOIN deliveries d ON d.id=l.delivery_id
        ) l
        JOIN evidence_versions v ON v.id = l.evidence_version_id
        GROUP BY v.evidence_file_id HAVING count(DISTINCT l.aid_case_id) > 1
    ) THEN
        RAISE EXCEPTION 'Legacy evidence ownership requires reconciliation before V6';
    END IF;
END; $$;

ALTER TABLE aid_cases DROP CONSTRAINT chk_case_priority;
UPDATE aid_cases SET priority = 'NORMAL' WHERE priority = 'MEDIUM';
ALTER TABLE aid_cases ALTER COLUMN priority SET DEFAULT 'NORMAL';
ALTER TABLE aid_cases ADD CONSTRAINT chk_case_priority CHECK (priority IN ('NORMAL','URGENT'));
ALTER TABLE aid_cases DROP CONSTRAINT chk_case_status;
UPDATE aid_cases SET status = 'CHANGES_REQUESTED' WHERE status = 'NEEDS_INFO';
ALTER TABLE aid_cases ADD CONSTRAINT chk_case_status CHECK (status IN (
    'DRAFT','SUBMITTED','CHANGES_REQUESTED','VERIFIED','REJECTED',
    'IN_PROGRESS','COMPLETED','DISPUTED','CANCELLED'));
ALTER TABLE aid_cases ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);
ALTER TABLE aid_cases ADD COLUMN evidence_revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE aid_cases ADD COLUMN internal_title VARCHAR(200);
UPDATE aid_cases SET internal_title = title;
ALTER TABLE aid_cases ALTER COLUMN internal_title SET NOT NULL;
ALTER TABLE aid_cases ADD COLUMN public_title VARCHAR(160);
ALTER TABLE aid_cases ADD COLUMN public_summary VARCHAR(2000);
ALTER TABLE aid_cases ADD COLUMN approximate_area VARCHAR(160);
ALTER TABLE aid_cases ADD COLUMN consent_record_id UUID;
ALTER TABLE aid_cases ADD COLUMN supersedes_case_id UUID;
ALTER TABLE aid_cases ADD CONSTRAINT uq_case_org UNIQUE (id, organization_id);
ALTER TABLE aid_cases ADD CONSTRAINT fk_superseded_case_org
    FOREIGN KEY (supersedes_case_id, organization_id) REFERENCES aid_cases(id, organization_id);
CREATE INDEX idx_cases_org_created ON aid_cases(organization_id, created_at, id);

ALTER TABLE consent_records ADD COLUMN organization_id UUID;
UPDATE consent_records cr SET organization_id = b.organization_id
    FROM beneficiaries b WHERE b.id = cr.beneficiary_id;
ALTER TABLE consent_records ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE consent_records ADD COLUMN basis VARCHAR(20) CHECK (basis IN ('CONSENT','REPRESENTATIVE'));
ALTER TABLE consent_records ADD COLUMN captured_at TIMESTAMPTZ;
ALTER TABLE consent_records ADD COLUMN public_summary_allowed BOOLEAN;
ALTER TABLE consent_records ADD CONSTRAINT uq_consent_owner UNIQUE(id, beneficiary_id, organization_id);
ALTER TABLE consent_records ADD CONSTRAINT fk_consent_beneficiary_org
    FOREIGN KEY(beneficiary_id, organization_id) REFERENCES beneficiaries(id, organization_id);
ALTER TABLE aid_cases ADD CONSTRAINT fk_case_consent_owner
    FOREIGN KEY(consent_record_id, beneficiary_id, organization_id)
    REFERENCES consent_records(id, beneficiary_id, organization_id);

ALTER TABLE case_need_items ALTER COLUMN unit TYPE VARCHAR(40);
ALTER TABLE case_need_items ADD COLUMN retired_at_version BIGINT;
ALTER TABLE case_need_items DROP CONSTRAINT uq_case_need_item;
CREATE UNIQUE INDEX uq_current_case_need ON case_need_items(aid_case_id, lower(item_name), lower(unit))
    WHERE retired_at_version IS NULL;

ALTER TABLE evidence_files ADD COLUMN aid_case_id UUID;
UPDATE evidence_files f SET aid_case_id = owners.aid_case_id FROM (
    SELECT DISTINCT v.evidence_file_id, l.aid_case_id FROM evidence_versions v JOIN (
        SELECT aid_case_id,evidence_version_id FROM case_evidence_links
        UNION ALL
        SELECT d.aid_case_id,l.evidence_version_id FROM delivery_evidence_links l JOIN deliveries d ON d.id=l.delivery_id
    ) l ON l.evidence_version_id = v.id
) owners WHERE owners.evidence_file_id = f.id;
ALTER TABLE evidence_files ADD CONSTRAINT uq_evidence_file_owner UNIQUE(id, aid_case_id, organization_id);
ALTER TABLE evidence_files ADD CONSTRAINT fk_evidence_file_case_org
    FOREIGN KEY(aid_case_id, organization_id) REFERENCES aid_cases(id, organization_id);
ALTER TABLE evidence_versions ADD COLUMN organization_id UUID;
ALTER TABLE evidence_versions ADD COLUMN aid_case_id UUID;
UPDATE evidence_versions v SET organization_id = f.organization_id, aid_case_id = f.aid_case_id
    FROM evidence_files f WHERE f.id = v.evidence_file_id;
ALTER TABLE evidence_versions ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE evidence_versions ADD COLUMN fixture_key VARCHAR(120);
ALTER TABLE evidence_versions ADD COLUMN supersedes_id UUID;
ALTER TABLE evidence_versions ADD CONSTRAINT uq_evidence_version_owner UNIQUE(id, aid_case_id, organization_id);
ALTER TABLE evidence_versions ADD CONSTRAINT fk_version_file_owner
    FOREIGN KEY(evidence_file_id, aid_case_id, organization_id)
    REFERENCES evidence_files(id, aid_case_id, organization_id);
ALTER TABLE evidence_versions ADD CONSTRAINT fk_supersedes_version_owner
    FOREIGN KEY(supersedes_id, aid_case_id, organization_id)
    REFERENCES evidence_versions(id, aid_case_id, organization_id);
CREATE UNIQUE INDEX uq_version_successor ON evidence_versions(supersedes_id) WHERE supersedes_id IS NOT NULL;
ALTER TABLE case_evidence_links ADD COLUMN organization_id UUID;
UPDATE case_evidence_links l SET organization_id = c.organization_id FROM aid_cases c WHERE c.id = l.aid_case_id;
ALTER TABLE case_evidence_links ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE case_evidence_links ADD COLUMN retired_at_version BIGINT;
ALTER TABLE case_evidence_links ADD CONSTRAINT fk_case_evidence_owner
    FOREIGN KEY(evidence_version_id, aid_case_id, organization_id)
    REFERENCES evidence_versions(id, aid_case_id, organization_id);

ALTER TABLE audit_events ADD COLUMN payload_private JSONB NOT NULL DEFAULT '{}'::jsonb;
CREATE TABLE case_revisions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    aid_case_id UUID NOT NULL,
    case_version BIGINT NOT NULL CHECK(case_version >= 0),
    snapshot_private JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(aid_case_id, case_version),
    FOREIGN KEY(aid_case_id, organization_id) REFERENCES aid_cases(id, organization_id)
);
CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL REFERENCES users(id),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    operation VARCHAR(120) NOT NULL,
    key VARCHAR(128) NOT NULL CHECK(length(key) BETWEEN 16 AND 128),
    request_digest CHAR(64) NOT NULL CHECK(request_digest ~ '^[0-9a-f]{64}$'),
    response_status INTEGER NOT NULL CHECK(response_status BETWEEN 200 AND 299),
    response_private JSONB NOT NULL,
    response_location VARCHAR(200),
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(actor_id, operation, key)
);

-- Immutable facts. Link retirement is a separate projection, never a bytes overwrite.
CREATE FUNCTION reject_immutable_fact_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Immutable business records cannot be changed'; END; $$;
CREATE TRIGGER consent_records_immutable BEFORE UPDATE OR DELETE ON consent_records
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_fact_change();
CREATE TRIGGER evidence_versions_immutable BEFORE UPDATE OR DELETE ON evidence_versions
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_fact_change();
CREATE TRIGGER evidence_files_immutable BEFORE UPDATE OR DELETE ON evidence_files
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_fact_change();
CREATE TRIGGER audit_events_immutable BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_fact_change();
CREATE TRIGGER case_revisions_immutable BEFORE UPDATE OR DELETE ON case_revisions
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_fact_change();
CREATE TRIGGER idempotency_records_immutable BEFORE UPDATE OR DELETE ON idempotency_records
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_fact_change();
