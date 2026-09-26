-- Contrato completo con validación previa y firma con trazabilidad
-- (retroalimentación 25/09, hallazgos 1, 3, 13 y 17).

ALTER TABLE contract_generation ALTER COLUMN status TYPE VARCHAR(20);
ALTER TABLE contract_generation ALTER COLUMN delivery_method TYPE VARCHAR(32);
-- Huella SHA-256 del PDF que se firma (sha256 ya existente es la del snapshot de datos).
ALTER TABLE contract_generation ADD COLUMN document_sha256    VARCHAR(64);
ALTER TABLE contract_generation ADD COLUMN missing_items      TEXT;
ALTER TABLE contract_generation ADD COLUMN variant_summary    TEXT;
ALTER TABLE contract_generation ADD COLUMN superseded_at      TIMESTAMPTZ;
ALTER TABLE contract_generation ADD COLUMN superseded_reason  TEXT;
ALTER TABLE contract_generation ADD COLUMN signed_package_key TEXT;

-- Los contratos anteriores se generaron con la vista previa de prototipo
-- (7 cláusulas resumidas, sin validar datos) y "Marcar firmado" solo
-- cambiaba un estado: ninguno es un contrato válido. Quedan sin efecto,
-- conservando su registro.
UPDATE contract_generation
   SET status = 'SUPERSEDED',
       superseded_at = now(),
       superseded_reason = 'Generado con la vista previa de prototipo anterior; no es un contrato válido. Genera una versión nueva.'
 WHERE status IN ('GENERATED', 'SIGNED', 'DELIVERED');

CREATE TABLE contract_signature (
    id                      UUID PRIMARY KEY,
    contract_generation_id  UUID NOT NULL REFERENCES contract_generation (id) ON DELETE CASCADE,
    expediente_id           UUID NOT NULL REFERENCES expediente (id) ON DELETE CASCADE,
    party                   VARCHAR(16) NOT NULL,
    participant_id          UUID,
    signer_name             TEXT NOT NULL,
    signer_capacity         TEXT NOT NULL,
    signer_email            TEXT,
    status                  VARCHAR(16) NOT NULL,
    token_hash              VARCHAR(64) UNIQUE,
    token_expires_at        TIMESTAMPTZ,
    requested_at            TIMESTAMPTZ NOT NULL,
    signed_at               TIMESTAMPTZ,
    method                  VARCHAR(24),
    document_sha256         VARCHAR(64),
    typed_name              TEXT,
    ip_address              TEXT,
    user_agent              TEXT,
    signature_image_key     TEXT,
    signature_image_sha256  VARCHAR(64),
    evidence_key            TEXT,
    evidence_sha256         VARCHAR(64),
    registered_by_user_id   UUID REFERENCES app_user (id),
    consent_text            TEXT,
    voided_at               TIMESTAMPTZ,
    voided_reason           TEXT
);

CREATE INDEX idx_contract_signature_contract ON contract_signature (contract_generation_id);
CREATE INDEX idx_contract_signature_expediente ON contract_signature (expediente_id);
