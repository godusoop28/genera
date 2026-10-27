-- Control de Cambios #31: acuse de recepción de documentos y aviso de
-- privacidad firmado por el representante legal de WILGEN.

-- Textos y firmante (una sola fila). Los textos los aprueba el área legal:
-- mientras approved = FALSE el acuse sale con la leyenda "texto pendiente de
-- aprobación legal" y no se puede enviar al cliente.
CREATE TABLE receipt_settings (
    id                     SMALLINT PRIMARY KEY,
    responsible_entity     TEXT NOT NULL,
    representative_name    TEXT,
    representative_title   TEXT,
    responsibility_text    TEXT NOT NULL,
    privacy_text           TEXT NOT NULL,
    approved               BOOLEAN NOT NULL DEFAULT FALSE,
    approved_by_name       TEXT,
    approved_at            TIMESTAMPTZ,
    updated_by_name        TEXT,
    updated_at             TIMESTAMPTZ NOT NULL
);

INSERT INTO receipt_settings (id, responsible_entity, representative_title, responsibility_text, privacy_text, updated_at) VALUES (
    1,
    'Grupo WILGEN y Asociados S. de R.L. de C.V.',
    'Representante legal',
    'Por medio del presente, la entidad responsable hace constar que recibió del cliente los documentos que se relacionan en este acuse, en la versión y fecha indicadas, y asume la responsabilidad de su custodia, resguardo y tratamiento conforme a la normatividad aplicable, utilizándolos únicamente para los fines de la operación inmobiliaria encomendada y con las medidas de seguridad administrativas, técnicas y físicas necesarias para protegerlos contra daño, pérdida, alteración, destrucción o uso, acceso o tratamiento no autorizado.',
    'Los datos personales contenidos en los documentos recibidos se tratan conforme al Aviso de Privacidad indicado en este acuse, el cual el cliente puede consultar en todo momento y que se adjunta a este documento.',
    now()
);

-- Cada acuse emitido. items_json: relación de documentos efectivamente
-- recibidos al momento de emitirlo (nombre, versión y fecha de cada archivo),
-- para saber siempre qué cubrió cada acuse. Los anteriores se conservan.
CREATE TABLE document_receipt (
    id                       UUID PRIMARY KEY,
    expediente_id            UUID NOT NULL REFERENCES expediente (id) ON DELETE CASCADE,
    sequence_number          INTEGER NOT NULL,
    status                   VARCHAR(20) NOT NULL,
    expediente_folio         VARCHAR(40) NOT NULL,
    property_key             VARCHAR(40),
    client_name              TEXT NOT NULL,
    client_email             VARCHAR(255),
    responsible_entity       TEXT NOT NULL,
    representative_name      TEXT,
    representative_title     TEXT,
    responsibility_text      TEXT NOT NULL,
    privacy_text             TEXT NOT NULL,
    texts_approved           BOOLEAN NOT NULL,
    privacy_notice_version   INTEGER,
    privacy_notice_sha256    VARCHAR(64),
    privacy_notice_key       TEXT,
    privacy_accepted_at      TIMESTAMPTZ,
    items_json               TEXT NOT NULL,
    item_count               INTEGER NOT NULL,
    draft_pdf_key            TEXT NOT NULL,
    draft_sha256             VARCHAR(64),
    signed_pdf_key           TEXT,
    signed_sha256            VARCHAR(64),
    signature_method         VARCHAR(24),
    signed_by_name           TEXT,
    signed_by_user_id        UUID,
    signed_at                TIMESTAMPTZ,
    sent_to                  VARCHAR(255),
    sent_channel             VARCHAR(16),
    sent_at                  TIMESTAMPTZ,
    sent_by_name             TEXT,
    send_detail              TEXT,
    requested_signature_at   TIMESTAMPTZ,
    requested_by_name        TEXT,
    generated_by_name        TEXT NOT NULL,
    generated_at             TIMESTAMPTZ NOT NULL,
    version                  BIGINT NOT NULL DEFAULT 0,
    UNIQUE (expediente_id, sequence_number)
);
CREATE INDEX idx_document_receipt_expediente ON document_receipt (expediente_id, sequence_number DESC);
CREATE INDEX idx_document_receipt_status ON document_receipt (status);

INSERT INTO permission (code, description) VALUES
    ('RECEIPT_GENERATE', 'Generar el acuse de recepción de documentos y enviarlo al cliente cuando está firmado'),
    ('RECEIPT_SIGN', 'Firmar el acuse como representante legal o cargar el acuse firmado'),
    ('RECEIPT_MANAGE', 'Editar y aprobar los textos del acuse (área legal) y el representante que firma');

INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, 'RECEIPT_GENERATE' FROM app_role r WHERE r.code IN ('ADMINISTRATOR', 'DIRECTOR', 'ADVISOR');

INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, p.code
  FROM app_role r
  CROSS JOIN permission p
 WHERE r.code = 'ADMINISTRATOR'
   AND p.code IN ('RECEIPT_SIGN', 'RECEIPT_MANAGE');
