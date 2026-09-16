-- Módulo privacy: versión exacta del aviso aceptado y evidencia de firma (ver AGENTS §63-67).

CREATE TABLE legal_template (
    id              UUID PRIMARY KEY,
    type            VARCHAR(32) NOT NULL,
    version         INT NOT NULL,
    effective_from  TIMESTAMPTZ NOT NULL,
    effective_to    TIMESTAMPTZ,
    sha256          VARCHAR(64) NOT NULL,
    storage_key     TEXT NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (type, version)
);

CREATE TABLE privacy_consent (
    id                          UUID PRIMARY KEY,
    expediente_id               UUID NOT NULL UNIQUE REFERENCES expediente (id) ON DELETE CASCADE,
    notice_template_id          UUID NOT NULL REFERENCES legal_template (id),
    main_purposes_accepted      BOOLEAN NOT NULL,
    secondary_purposes_accepted BOOLEAN NOT NULL,
    accepted_at                 TIMESTAMPTZ NOT NULL,
    ip_address                  TEXT NOT NULL,
    user_agent                  TEXT,
    signature_storage_key       TEXT,
    signature_sha256            VARCHAR(64)
);
