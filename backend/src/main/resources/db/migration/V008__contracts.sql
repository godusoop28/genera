-- Módulo contracts: snapshot inmutable de cada contrato generado (ver AGENTS §75).

CREATE TABLE contract_generation (
    id               UUID PRIMARY KEY,
    expediente_id    UUID NOT NULL REFERENCES expediente (id) ON DELETE CASCADE,
    version_number   INT NOT NULL,
    snapshot_json    TEXT NOT NULL,
    docx_storage_key TEXT NOT NULL,
    pdf_storage_key  TEXT,
    generated_at     TIMESTAMPTZ NOT NULL,
    generated_by     UUID NOT NULL REFERENCES app_user (id),
    sha256           VARCHAR(64) NOT NULL,
    status           VARCHAR(16) NOT NULL,
    signed_at        TIMESTAMPTZ,
    delivered_at     TIMESTAMPTZ,
    delivery_method  VARCHAR(16),
    UNIQUE (expediente_id, version_number)
);

CREATE INDEX idx_contract_generation_expediente ON contract_generation (expediente_id);
