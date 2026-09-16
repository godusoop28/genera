-- Módulo documents: documento lógico separado del archivo físico (ver AGENTS §24-27).

CREATE TABLE document (
    id                   UUID PRIMARY KEY,
    expediente_id        UUID NOT NULL REFERENCES expediente (id) ON DELETE CASCADE,
    requirement_code     VARCHAR(64) NOT NULL,
    type                 VARCHAR(32) NOT NULL,
    participant_id       UUID,
    required             BOOLEAN NOT NULL,
    status               VARCHAR(24) NOT NULL,
    current_version_number INT NOT NULL DEFAULT 0,
    version              BIGINT NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    UNIQUE (expediente_id, requirement_code)
);

CREATE INDEX idx_document_expediente ON document (expediente_id);

CREATE TABLE document_version (
    id                     UUID PRIMARY KEY,
    document_id            UUID NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    version_number         INT NOT NULL,
    storage_key_pdf        TEXT,
    storage_key_normalized TEXT,
    uploaded_at            TIMESTAMPTZ NOT NULL,
    uploaded_via           VARCHAR(16) NOT NULL,
    processing_status      VARCHAR(16) NOT NULL,
    processing_error       TEXT,
    UNIQUE (document_id, version_number)
);

CREATE INDEX idx_document_version_document ON document_version (document_id);

CREATE TABLE document_page (
    id                    UUID PRIMARY KEY,
    document_version_id   UUID NOT NULL REFERENCES document_version (id) ON DELETE CASCADE,
    page_number           INT NOT NULL,
    storage_key_original  TEXT NOT NULL,
    original_filename     TEXT NOT NULL,
    mime_type             TEXT NOT NULL,
    size                  BIGINT NOT NULL,
    sha256                VARCHAR(64) NOT NULL,
    UNIQUE (document_version_id, page_number)
);

CREATE TABLE document_review (
    id                    UUID PRIMARY KEY,
    document_version_id   UUID NOT NULL REFERENCES document_version (id) ON DELETE CASCADE,
    decision              VARCHAR(16) NOT NULL,
    reason_code           VARCHAR(32),
    comment               TEXT,
    reviewed_by           UUID NOT NULL REFERENCES app_user (id),
    reviewed_at           TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_document_review_version ON document_review (document_version_id);
