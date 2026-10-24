-- Pertenencia de usuarios a organizaciones con su rol dentro de ella.
-- Roles en mayúsculas (igual que el enum OrgRole y la API); platform_role es otra cosa y no entra aquí
CREATE TABLE membership (
    organization_id  UUID NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    user_id          UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role             VARCHAR(20) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_membership PRIMARY KEY (organization_id, user_id),
    CONSTRAINT ck_membership_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER'))
);

-- Un solo OWNER por organización
CREATE UNIQUE INDEX uq_membership_one_owner ON membership (organization_id) WHERE role = 'OWNER';

-- Organizaciones de un usuario
CREATE INDEX idx_membership_user_id ON membership (user_id);

-- Backfill: el dueño de cada organización existente pasa a ser su OWNER
INSERT INTO membership (organization_id, user_id, role, created_at, updated_at)
SELECT id, owner_id, 'OWNER', created_at, created_at
FROM organization;
