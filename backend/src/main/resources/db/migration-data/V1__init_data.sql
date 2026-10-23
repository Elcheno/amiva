-- Sin FK hacia core: la integridad entre bases la garantiza el backend

-- Envío de un formulario. El id es el submission_id que genera el backend (idempotencia)
CREATE TABLE submission (
    id               UUID PRIMARY KEY DEFAULT uuidv7(),
    org_id           UUID NOT NULL,
    participant_id   UUID NOT NULL,
    form_version_id  UUID NOT NULL,
    opening_id       UUID NOT NULL,
    submitted_at     TIMESTAMPTZ NOT NULL,     -- reloj del servidor
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Valor numérico de un campo dentro de un envío
CREATE TABLE answer (
    id               UUID PRIMARY KEY DEFAULT uuidv7(),
    submission_id    UUID NOT NULL REFERENCES submission (id) ON DELETE RESTRICT,
    org_id           UUID NOT NULL,
    form_version_id  UUID NOT NULL,
    field_id         UUID NOT NULL,
    participant_id   UUID NOT NULL,
    ts               TIMESTAMPTZ NOT NULL,
    value            NUMERIC NOT NULL,

    CONSTRAINT uq_answer_submission_field UNIQUE (submission_id, field_id)
);

-- Series por campo y por participante
CREATE INDEX idx_answer_org_field_ts ON answer (org_id, field_id, ts);
CREATE INDEX idx_answer_org_participant_field_ts ON answer (org_id, participant_id, field_id, ts);

-- Inmutabilidad: submission y answer solo admiten INSERT
CREATE FUNCTION reject_modification() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'La tabla % es inmutable: % no permitido', TG_TABLE_NAME, TG_OP;
END;
$$;

CREATE TRIGGER trg_submission_immutable
    BEFORE UPDATE OR DELETE ON submission
    FOR EACH ROW EXECUTE FUNCTION reject_modification();

CREATE TRIGGER trg_answer_immutable
    BEFORE UPDATE OR DELETE ON answer
    FOR EACH ROW EXECUTE FUNCTION reject_modification();

-- TRUNCATE no dispara triggers de fila: se bloquea aparte
CREATE TRIGGER trg_submission_no_truncate
    BEFORE TRUNCATE ON submission
    FOR EACH STATEMENT EXECUTE FUNCTION reject_modification();

CREATE TRIGGER trg_answer_no_truncate
    BEFORE TRUNCATE ON answer
    FOR EACH STATEMENT EXECUTE FUNCTION reject_modification();
