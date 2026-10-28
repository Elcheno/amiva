-- Aceptar o rechazar una invitación: la fila se conserva con su estado y la hora de la respuesta.
-- uq_invitation_pending no cambia: solo cuenta las PENDING, así que se puede reinvitar a quien rechazó
ALTER TABLE invitation DROP CONSTRAINT ck_invitation_status;
ALTER TABLE invitation ADD CONSTRAINT ck_invitation_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED'));

-- NULL mientras está pendiente
ALTER TABLE invitation ADD COLUMN responded_at TIMESTAMPTZ;
