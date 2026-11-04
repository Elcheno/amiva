-- Grupos de miembros de una organización, para seleccionarlos rápido en informes futuros.
-- Solo guardan los miembros actuales: quitar a alguien borra la fila (altas y bajas quedan en activity)
CREATE TABLE member_group (
    id               UUID PRIMARY KEY DEFAULT uuidv7(),
    organization_id  UUID NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    name             VARCHAR(60) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_member_group_name CHECK (char_length(btrim(name)) > 0),
    -- Permite que member_group_member apunte al grupo junto con su organización
    CONSTRAINT uq_member_group_id_org UNIQUE (id, organization_id)
);

-- Nombre único por organización sin distinguir mayúsculas
CREATE UNIQUE INDEX uq_member_group_org_name ON member_group (organization_id, lower(name));

-- Miembros actuales de cada grupo. Si el usuario sale de la organización (membership), sale de sus grupos.
-- organization_id va en ambas FK compuestas: el miembro y el grupo han de ser de la misma organización
CREATE TABLE member_group_member (
    group_id         UUID NOT NULL,
    organization_id  UUID NOT NULL,
    user_id          UUID NOT NULL,
    added_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_member_group_member PRIMARY KEY (group_id, user_id),
    CONSTRAINT fk_member_group_member_group FOREIGN KEY (group_id, organization_id)
        REFERENCES member_group (id, organization_id) ON DELETE CASCADE,
    CONSTRAINT fk_member_group_member_membership FOREIGN KEY (organization_id, user_id)
        REFERENCES membership (organization_id, user_id) ON DELETE CASCADE
);

-- Grupos de un miembro
CREATE INDEX idx_member_group_member_org_user ON member_group_member (organization_id, user_id);
