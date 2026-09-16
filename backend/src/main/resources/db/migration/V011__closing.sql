-- Módulo closing: seguimiento genérico de cierre (ver AGENTS §50). No
-- inventa documentos legalmente requeridos que no estén especificados.

CREATE TABLE closing_case (
    id                    UUID PRIMARY KEY,
    expediente_id         UUID NOT NULL UNIQUE REFERENCES expediente (id) ON DELETE CASCADE,
    status                VARCHAR(16) NOT NULL,
    contract_delivered    BOOLEAN NOT NULL DEFAULT FALSE,
    contract_delivered_at TIMESTAMPTZ,
    version               BIGINT NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL
);

CREATE TABLE closing_note (
    id               UUID PRIMARY KEY,
    closing_case_id  UUID NOT NULL REFERENCES closing_case (id) ON DELETE CASCADE,
    author_user_id   UUID NOT NULL REFERENCES app_user (id),
    note             VARCHAR(2000) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_closing_note_case ON closing_note (closing_case_id, created_at);
