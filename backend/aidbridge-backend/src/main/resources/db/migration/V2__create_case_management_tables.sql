CREATE TABLE beneficiaries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    reference_code VARCHAR(50) NOT NULL,
    beneficiary_type VARCHAR(20) NOT NULL DEFAULT 'HOUSEHOLD',
    display_label VARCHAR(150) NOT NULL,
    private_data_encrypted TEXT,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_beneficiary_reference UNIQUE (organization_id, reference_code),
    CONSTRAINT uq_beneficiary_org_id UNIQUE (id, organization_id),
    CONSTRAINT chk_beneficiary_type
        CHECK (beneficiary_type IN ('HOUSEHOLD', 'INDIVIDUAL'))
);

CREATE INDEX idx_beneficiaries_organization
    ON beneficiaries (organization_id);

CREATE TABLE aid_cases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_code VARCHAR(50) NOT NULL UNIQUE,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    beneficiary_id UUID NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id),
    title VARCHAR(200) NOT NULL,
    public_description TEXT NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    region_label VARCHAR(150),
    submitted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- An organization cannot attach another organization's private beneficiary.
    CONSTRAINT fk_case_beneficiary_same_org
        FOREIGN KEY (beneficiary_id, organization_id)
            REFERENCES beneficiaries (id, organization_id),
    CONSTRAINT chk_case_priority
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT chk_case_status
         CHECK (status IN (
            'DRAFT', 'SUBMITTED', 'NEEDS_INFO', 'VERIFIED', 'REJECTED',
            'IN_PROGRESS', 'COMPLETED', 'DISPUTED', 'CANCELLED'
         ))
);

CREATE INDEX idx_aid_cases_org_status
    ON aid_cases (organization_id, status);
CREATE INDEX idx_aid_cases_beneficiary
    ON aid_cases (beneficiary_id);
CREATE INDEX idx_aid_cases_created_by
    ON aid_cases (created_by);

CREATE TABLE case_need_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aid_case_id UUID NOT NULL REFERENCES aid_cases(id),
    item_name VARCHAR(120) NOT NULL,
    unit VARCHAR(30) NOT NULL DEFAULT 'PACKAGE',
    requested_quantity INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_case_need_item UNIQUE (aid_case_id, item_name, unit),
    CONSTRAINT chk_case_need_quantity CHECK (requested_quantity > 0)
);

CREATE INDEX idx_case_need_items_case
    ON case_need_items (aid_case_id);

CREATE TABLE verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aid_case_id UUID NOT NULL REFERENCES aid_cases(id),
    verifier_id UUID NOT NULL REFERENCES users(id),
    decision VARCHAR(20) NOT NULL,
    notes TEXT,
    verified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_verification_decision
        CHECK (decision IN ('APPROVED', 'REJECTED', 'NEEDS_INFO'))
);

CREATE INDEX idx_verifications_case_time
    ON verifications (aid_case_id, verified_at DESC);
CREATE INDEX idx_verifications_verifier
    ON verifications (verifier_id);