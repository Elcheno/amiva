-- Registro de actividad de cada organización: quién hizo qué y cuándo. Solo INSERT.
-- actor_id sin FK: el nombre va copiado y un ON DELETE SET NULL sería un UPDATE (bloqueado)
CREATE TABLE activity (
    id               UUID PRIMARY KEY DEFAULT uuidv7(),
    organization_id  UUID NOT NULL REFERENCES organization (id) ON DELETE RESTRICT,
    actor_id         UUID,
    actor_name       VARCHAR(150) NOT NULL,
    anonymous        BOOLEAN NOT NULL DEFAULT false,   -- superadmin: se muestra sin identidad
    action           VARCHAR(50) NOT NULL,
    target_type      VARCHAR(30) NOT NULL,
    target_id        UUID NOT NULL,
    details          JSONB NOT NULL DEFAULT '{}',      -- datos para montar el texto (nombres copiados, valores)
    occurred_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Actividad de una organización, de más reciente a más antigua
CREATE INDEX idx_activity_org_occurred_at ON activity (organization_id, occurred_at DESC);

-- Inmutabilidad: igual que submission y answer en la base data
CREATE FUNCTION reject_modification() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'La tabla % es inmutable: % no permitido', TG_TABLE_NAME, TG_OP;
END;
$$;

CREATE TRIGGER trg_activity_immutable
    BEFORE UPDATE OR DELETE ON activity
    FOR EACH ROW EXECUTE FUNCTION reject_modification();

-- TRUNCATE no dispara triggers de fila: se bloquea aparte
CREATE TRIGGER trg_activity_no_truncate
    BEFORE TRUNCATE ON activity
    FOR EACH STATEMENT EXECUTE FUNCTION reject_modification();
