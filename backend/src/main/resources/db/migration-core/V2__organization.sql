-- Organizaciones. De momento cada usuario es dueño de como máximo una (la propia)
CREATE TABLE organization (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    name        VARCHAR(150) NOT NULL,
    timezone    VARCHAR(64) NOT NULL,          -- zona IANA, p. ej. Europe/Madrid
    owner_id    UUID NOT NULL UNIQUE REFERENCES app_user (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
