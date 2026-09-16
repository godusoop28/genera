-- Módulo publicaccess: ligas de clientes sin cuenta (ver AGENTS §13/§14).

CREATE TABLE public_access_token (
    id             UUID PRIMARY KEY,
    expediente_id  UUID NOT NULL REFERENCES expediente (id) ON DELETE CASCADE,
    token_hash     VARCHAR(64) NOT NULL UNIQUE,
    created_at     TIMESTAMPTZ NOT NULL,
    expires_at     TIMESTAMPTZ,
    revoked_at     TIMESTAMPTZ,
    last_used_at   TIMESTAMPTZ,
    active         BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_public_access_token_expediente ON public_access_token (expediente_id);
CREATE INDEX idx_public_access_token_active ON public_access_token (expediente_id, active);
