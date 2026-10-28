-- Invitaciones a una organización para usuarios ya registrados. Solo se guarda el SHA-256 del token (hex).
-- Pendientes sin caducidad. Reinvitar a quien ya tiene una pendiente reutiliza la fila (token, rol e invitador nuevos)
CREATE TABLE invitation (
    id                  UUID PRIMARY KEY DEFAULT uuidv7(),
    organization_id     UUID NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    invitee_user_id     UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    invited_by_user_id  UUID REFERENCES app_user (id) ON DELETE SET NULL,   -- NULL si el invitador ya no existe
    role                VARCHAR(20) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',             -- aceptar/rechazar llegará después
    token_hash          CHAR(64) NOT NULL UNIQUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_invitation_role CHECK (role IN ('ADMIN', 'MEMBER')),
    CONSTRAINT ck_invitation_status CHECK (status IN ('PENDING'))
);

-- Una sola invitación pendiente por usuario y organización
CREATE UNIQUE INDEX uq_invitation_pending ON invitation (organization_id, invitee_user_id) WHERE status = 'PENDING';

-- Invitaciones recibidas por un usuario
CREATE INDEX idx_invitation_invitee_user_id ON invitation (invitee_user_id);
