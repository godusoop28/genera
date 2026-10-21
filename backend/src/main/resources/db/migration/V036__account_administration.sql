-- Control de Cambios #25: administración de cuentas del personal interno.
--
-- * Nadie conoce la contraseña de otro usuario: el alta y el restablecimiento
--   generan un enlace de un solo uso (solo se guarda su hash) para que la
--   persona defina la suya. Una cuenta recién creada no tiene contraseña
--   (status PENDING) hasta que usa su enlace.
-- * Bloqueo temporal tras varios intentos fallidos de acceso.
-- * sessions_valid_after: los access tokens emitidos antes de esta fecha dejan
--   de valer (desactivar, cambiar contraseña o rol surte efecto de inmediato).
-- * "Dar de baja" (OFFBOARDED) es definitivo y conserva el historial: el
--   usuario nunca se borra.

ALTER TABLE app_user ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE app_user
    ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN locked_until          TIMESTAMPTZ,
    ADD COLUMN sessions_valid_after  TIMESTAMPTZ,
    ADD COLUMN password_changed_at   TIMESTAMPTZ,
    ADD COLUMN offboarded_at         TIMESTAMPTZ;

CREATE TABLE password_setup_token (
    id                 UUID PRIMARY KEY,
    user_id            UUID         NOT NULL REFERENCES app_user (id),
    token_hash         VARCHAR(64)  NOT NULL UNIQUE,
    purpose            VARCHAR(16)  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    expires_at         TIMESTAMPTZ  NOT NULL,
    used_at            TIMESTAMPTZ,
    revoked_at         TIMESTAMPTZ,
    created_by_user_id UUID
);

CREATE INDEX idx_password_setup_token_user ON password_setup_token (user_id);

-- Bitácora de cuentas: se consulta por tipo de agregado ("User").
CREATE INDEX IF NOT EXISTS idx_audit_event_aggregate_type ON audit_event (aggregate_type, occurred_at DESC);

UPDATE permission SET description = 'Gestionar usuarios: alta, edición, desactivación, baja y restablecimiento de contraseña'
 WHERE code = 'USER_MANAGE';
UPDATE permission SET description = 'Asignar o cambiar el rol de un usuario'
 WHERE code = 'ROLE_MANAGE';
