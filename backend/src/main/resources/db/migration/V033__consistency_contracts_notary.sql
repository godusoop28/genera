-- Controles de cambios #15, #17, #19, #20 y #23 (16/10).

-- #17: autorizar inconsistencias es decisión de un directivo o administrador, con contraseña.
ALTER TABLE data_conflict ADD COLUMN resolved_by_name VARCHAR(160);

INSERT INTO permission (code, description) VALUES
    ('INCONSISTENCY_AUTHORIZE', 'Autorizar inconsistencias entre documentos (se confirma con la contraseña)');

INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, 'INCONSISTENCY_AUTHORIZE' FROM app_role r WHERE r.code IN ('ADMINISTRATOR', 'DIRECTOR');

-- #19: valores posibles de un dato (antecedentes de la escritura) y quién confirmó el elegido.
ALTER TABLE extracted_field_observation ADD COLUMN alternatives_json TEXT;
ALTER TABLE extracted_field_observation ADD COLUMN confirmed_by_user_id UUID;
ALTER TABLE extracted_field_observation ADD COLUMN confirmed_by_name VARCHAR(160);
ALTER TABLE extracted_field_observation ADD COLUMN confirmed_at TIMESTAMPTZ;

-- #20: contratos firmados fuera del sistema (DocuSign o en papel), también históricos.
CREATE TABLE signed_contract_upload (
    id                  UUID PRIMARY KEY,
    expediente_id       UUID         NOT NULL,
    document_id         UUID         NOT NULL,
    signature_method    VARCHAR(16)  NOT NULL,
    historical          BOOLEAN      NOT NULL DEFAULT FALSE,
    note                TEXT,
    uploaded_by_user_id UUID,
    uploaded_by_name    VARCHAR(160),
    uploaded_at         TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_signed_contract_upload_expediente ON signed_contract_upload (expediente_id);

-- #23: etapa de cierre (oferta aceptada), vigencia de documentos de cierre y envíos a notaría.
CREATE TABLE notary_stage (
    expediente_id          UUID PRIMARY KEY,
    offer_accepted_at      TIMESTAMPTZ,
    offer_accepted_by_name VARCHAR(160),
    offer_note             TEXT
);

CREATE TABLE closing_document_check (
    document_id         UUID PRIMARY KEY,
    expediente_id       UUID         NOT NULL,
    document_version_id UUID         NOT NULL,
    valid               BOOLEAN      NOT NULL,
    note                TEXT,
    checked_by_name     VARCHAR(160),
    checked_at          TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_closing_document_check_expediente ON closing_document_check (expediente_id);

CREATE TABLE notary_submission (
    id               UUID PRIMARY KEY,
    expediente_id    UUID         NOT NULL,
    kind             VARCHAR(16)  NOT NULL,
    recipient_email  VARCHAR(254),
    note             TEXT,
    warnings         TEXT,
    sent_by_user_id  UUID,
    sent_by_name     VARCHAR(160),
    sent_at          TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_notary_submission_expediente ON notary_submission (expediente_id);

CREATE TABLE notary_submission_item (
    id                  UUID PRIMARY KEY,
    submission_id       UUID         NOT NULL REFERENCES notary_submission (id) ON DELETE CASCADE,
    position            INT          NOT NULL,
    document_id         UUID         NOT NULL,
    document_version_id UUID         NOT NULL,
    version_number      INT          NOT NULL,
    document_name       VARCHAR(200) NOT NULL
);
CREATE INDEX idx_notary_submission_item_submission ON notary_submission_item (submission_id);
