-- Nombre de usuario único (estilo GitHub): minúsculas, dígitos y guiones sueltos entre medias, 3-39 caracteres
ALTER TABLE app_user ADD COLUMN username VARCHAR(39);

-- Backfill: los usuarios existentes reciben uno provisional (podrán cambiarlo más adelante)
UPDATE app_user
SET username = 'user-' || substr(md5(random()::text || id::text), 1, 8)
WHERE username IS NULL;

ALTER TABLE app_user ALTER COLUMN username SET NOT NULL;

ALTER TABLE app_user ADD CONSTRAINT ck_app_user_username
    CHECK (username ~ '^[a-z0-9]+(-[a-z0-9]+)*$' AND char_length(username) BETWEEN 3 AND 39);

-- Username único sin distinguir mayúsculas
CREATE UNIQUE INDEX uq_app_user_username_lower ON app_user (lower(username));
