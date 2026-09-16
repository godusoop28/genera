-- Módulo extraction: observaciones de IA separadas del dato canónico, y
-- conflictos entre documentos sin resolución automática (ver AGENTS §38-41).

CREATE TABLE extracted_field_observation (
    id                  UUID PRIMARY KEY,
    expediente_id       UUID NOT NULL REFERENCES expediente (id) ON DELETE CASCADE,
    document_id         UUID NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    document_version_id UUID NOT NULL REFERENCES document_version (id) ON DELETE CASCADE,
    field_name          VARCHAR(64) NOT NULL,
    detected_value      TEXT,
    confirmed_value     TEXT,
    origin              VARCHAR(16) NOT NULL,
    confidence          DOUBLE PRECISION,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_extracted_field_document ON extracted_field_observation (document_id);
CREATE INDEX idx_extracted_field_expediente ON extracted_field_observation (expediente_id, field_name);

CREATE TABLE data_conflict (
    id                  UUID PRIMARY KEY,
    expediente_id       UUID NOT NULL REFERENCES expediente (id) ON DELETE CASCADE,
    field_name          VARCHAR(64) NOT NULL,
    description         TEXT NOT NULL,
    detected_at         TIMESTAMPTZ NOT NULL,
    resolved            BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_at         TIMESTAMPTZ,
    resolved_by_user_id UUID REFERENCES app_user (id),
    resolution_note     TEXT
);

CREATE INDEX idx_data_conflict_expediente ON data_conflict (expediente_id, resolved);
