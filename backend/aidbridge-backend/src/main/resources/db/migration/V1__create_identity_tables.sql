CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_organization_status
        CHECK (status IN ('PENDING', 'APPROVED', 'SUSPENDED'))
);

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    platform_role VARCHAR(30) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_user_status
        CHECK (status IN ('ACTIVE', 'DISABLED')),

    CONSTRAINT chk_platform_role
        CHECK (platform_role IN ('USER', 'PLATFORM_ADMIN'))
);

CREATE UNIQUE INDEX uq_users_email
    ON users (LOWER(email));

CREATE TABLE organization_memberships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    user_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_membership_organization
        FOREIGN KEY (organization_id)
            REFERENCES organizations(id),

    CONSTRAINT fk_membership_user
        FOREIGN KEY (user_id)
            REFERENCES users(id),

    CONSTRAINT uq_organization_user
        UNIQUE (organization_id, user_id),

    CONSTRAINT chk_membership_status
        CHECK (status IN ('ACTIVE', 'INVITED', 'DISABLED'))
);

CREATE INDEX idx_memberships_user
    ON organization_memberships(user_id);

CREATE TABLE membership_roles (
    membership_id UUID NOT NULL,
    role VARCHAR(40) NOT NULL,

    PRIMARY KEY (membership_id, role),

    CONSTRAINT fk_role_membership
        FOREIGN KEY (membership_id)
            REFERENCES organization_memberships(id)
            ON DELETE CASCADE,

    CONSTRAINT chk_membership_role
        CHECK (role IN (
            'ORG_ADMIN',
            'VOLUNTEER',
            'VERIFIER',
            'DELIVERY_CONFIRMER'
        ))
)