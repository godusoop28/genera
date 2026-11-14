-- Control de Cambios #44: liga exclusiva para que el cliente firme el aviso
-- de privacidad de SU expediente (no da acceso al resto del expediente).
-- Solo se guarda el hash del token. Una liga activa por expediente; deja de
-- funcionar al usarse, al vencer, al revocarla o al generar otra.
CREATE TABLE privacy_sign_link (
    id                  UUID PRIMARY KEY,
    expediente_id       UUID NOT NULL,
    token_hash          VARCHAR(64) NOT NULL UNIQUE,
    created_at          TIMESTAMPTZ NOT NULL,
    created_by_user_id  UUID,
    created_by_name     TEXT,
    expires_at          TIMESTAMPTZ NOT NULL,
    revoked_at          TIMESTAMPTZ,
    used_at             TIMESTAMPTZ,
    last_opened_at      TIMESTAMPTZ
);
CREATE INDEX idx_privacy_sign_link_expediente ON privacy_sign_link (expediente_id, created_at DESC);

-- Por dónde se firmó digitalmente: PORTAL (liga del expediente) o SIGN_LINK
-- (liga exclusiva del aviso). NULL en registros anteriores y en firma en papel.
ALTER TABLE privacy_consent ADD COLUMN signature_channel VARCHAR(16);
