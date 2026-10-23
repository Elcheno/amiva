-- Usuarios de la plataforma
CREATE TABLE app_user (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    email           VARCHAR(320) NOT NULL,
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    password_hash   VARCHAR(255),              -- NULL en usuarios solo OAuth
    display_name    VARCHAR(150) NOT NULL,
    platform_role   VARCHAR(20) NOT NULL DEFAULT 'user',
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_app_user_platform_role CHECK (platform_role IN ('user', 'superadmin'))
);

-- Email único sin distinguir mayúsculas
CREATE UNIQUE INDEX uq_app_user_email_lower ON app_user (lower(email));

-- Identidades externas (OAuth) vinculadas a un usuario
CREATE TABLE user_identity (
    id                UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id           UUID NOT NULL REFERENCES app_user (id),
    provider          VARCHAR(50) NOT NULL,    -- github, google
    provider_subject  VARCHAR(255) NOT NULL,   -- id del usuario en el proveedor
    email             VARCHAR(320),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_user_identity_provider_subject UNIQUE (provider, provider_subject)
);

CREATE INDEX idx_user_identity_user_id ON user_identity (user_id);
