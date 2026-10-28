-- Tokens de verificación del email. Solo se guarda el SHA-256 del token (hex), nunca el token en claro.
-- Un solo uso (used_at) y caducan a las 24 h. Al reenviar se borran los anteriores sin usar
CREATE TABLE email_verification_token (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id     UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash  CHAR(64) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ                    -- NULL mientras no se haya usado
);

-- Tokens de un usuario (último emitido para limitar reenvíos, borrar los pendientes)
CREATE INDEX idx_email_verification_token_user_id ON email_verification_token (user_id);
