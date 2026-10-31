-- Control de Cambios #32: esquema de cierre de operaciones propias y externas
-- con revisión documental de la operación, PLD y PROFECO.
--
-- El estado de la operación (en proceso / en revisión de cierre / cerrada /
-- cancelada) es independiente del estado de la revisión documental
-- (pendiente / en revisión / completa y validada). Una operación cerrada con
-- faltantes se muestra como "Cerrada — Documentación pendiente".

CREATE SEQUENCE closing_operation_folio_seq;

CREATE TABLE closing_operation (
    id                      UUID PRIMARY KEY,
    folio                   VARCHAR(32) NOT NULL UNIQUE,
    origin                  VARCHAR(16) NOT NULL,          -- OWN | EXTERNAL
    expediente_id           UUID REFERENCES expediente (id) ON DELETE SET NULL,
    operation_type          VARCHAR(16) NOT NULL,          -- SALE | RENT
    property_description    TEXT NOT NULL,
    property_address        TEXT,
    -- Propiedad externa: de dónde viene y quién participa (sin captación propia).
    external_source         VARCHAR(160),
    external_office         VARCHAR(200),
    external_contact        VARCHAR(200),
    genera_participation    TEXT,
    represented_side        VARCHAR(16) NOT NULL,          -- SELLER | BUYER | BOTH
    advisor_id              UUID,
    advisor_name            VARCHAR(160),
    status                  VARCHAR(20) NOT NULL,          -- IN_PROCESS | CLOSING_REVIEW | CLOSED | CANCELLED
    closed_on               DATE,
    closed_by_name          VARCHAR(160),
    amount                  NUMERIC(16, 2),
    currency                VARCHAR(3),
    cancel_reason           TEXT,
    doc_status              VARCHAR(24) NOT NULL,          -- PENDING | IN_REVIEW | COMPLETE_VALIDATED
    doc_due_date            DATE,
    doc_follow_up_owner     VARCHAR(160),
    doc_authorized_by_name  VARCHAR(160),
    doc_authorized_by_id    UUID,
    doc_authorized_at       TIMESTAMPTZ,
    created_by_user_id      UUID NOT NULL,
    created_by_name         VARCHAR(160),
    version                 BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL
);
-- Una operación vigente por expediente (las canceladas no cuentan).
CREATE UNIQUE INDEX uq_closing_operation_expediente ON closing_operation (expediente_id) WHERE expediente_id IS NOT NULL AND status <> 'CANCELLED';
CREATE INDEX idx_closing_operation_status ON closing_operation (status, doc_status);
CREATE INDEX idx_closing_operation_closed_on ON closing_operation (closed_on);
CREATE INDEX idx_closing_operation_advisor ON closing_operation (advisor_id);

-- Vendedor / comprador (en renta: arrendador / arrendatario), con su representante.
CREATE TABLE closing_operation_party (
    id                      UUID PRIMARY KEY,
    operation_id            UUID NOT NULL REFERENCES closing_operation (id) ON DELETE CASCADE,
    side                    VARCHAR(16) NOT NULL,          -- SELLER | BUYER
    full_name               VARCHAR(200) NOT NULL,
    person_type             VARCHAR(16),                   -- INDIVIDUAL | LEGAL_ENTITY
    representative_name     VARCHAR(200),
    representative_role     VARCHAR(120),
    email                   VARCHAR(255),
    phone                   VARCHAR(40),
    counterpart_contact     VARCHAR(200),                  -- asesor/oficina/tercero por el que se piden sus papeles
    notes                   TEXT,
    UNIQUE (operation_id, side)
);

-- Matriz documental configurable. La aprueba el área legal o responsable de
-- cumplimiento; mientras no esté aprobada, ningún expediente de cierre puede
-- declararse "Completo y validado". Los renglones iniciales son un BORRADOR
-- de trabajo para que el área legal los revise: no acreditan cumplimiento.
CREATE TABLE closing_matrix_settings (
    id                 SMALLINT PRIMARY KEY,
    approved           BOOLEAN NOT NULL DEFAULT FALSE,
    approved_by_name   VARCHAR(160),
    approved_at        TIMESTAMPTZ,
    revision           INTEGER NOT NULL DEFAULT 1,
    updated_by_name    VARCHAR(160),
    updated_at         TIMESTAMPTZ NOT NULL
);
INSERT INTO closing_matrix_settings (id, updated_at) VALUES (1, now());

CREATE TABLE closing_requirement_template (
    id                 UUID PRIMARY KEY,
    section            VARCHAR(16) NOT NULL,               -- OPERATION | PLD | PROFECO
    party_side         VARCHAR(16),                        -- SELLER | BUYER | NULL (de la operación)
    operation_type     VARCHAR(16),                        -- SALE | RENT | NULL (ambas)
    origin             VARCHAR(16),                        -- OWN | EXTERNAL | NULL (ambos)
    label              VARCHAR(240) NOT NULL,
    description        TEXT,
    mandatory          BOOLEAN NOT NULL DEFAULT FALSE,     -- obligación legal: no admite "No aplica"
    validity_days      INTEGER,                            -- vigencia desde la fecha del documento
    document_type_hint VARCHAR(40),                        -- tipo de documento del expediente que se puede reutilizar
    sort_order         INTEGER NOT NULL DEFAULT 0,
    active             BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO closing_requirement_template (id, section, party_side, operation_type, origin, label, description, mandatory, validity_days, document_type_hint, sort_order) VALUES
    -- Documentación de la operación: vendedor / arrendador
    (gen_random_uuid(), 'OPERATION', 'SELLER', NULL, NULL, 'Identificación oficial vigente', 'INE o pasaporte de cada titular y, en su caso, de su representante.', TRUE, NULL, 'INE', 10),
    (gen_random_uuid(), 'OPERATION', 'SELLER', NULL, NULL, 'Comprobante de domicilio', 'No mayor a 3 meses.', FALSE, 90, 'PROOF_OF_ADDRESS', 20),
    (gen_random_uuid(), 'OPERATION', 'SELLER', NULL, NULL, 'Constancia de situación fiscal', NULL, FALSE, NULL, 'TAX_STATUS_CERTIFICATE', 30),
    (gen_random_uuid(), 'OPERATION', 'SELLER', NULL, NULL, 'Poder o acta constitutiva del representante', 'Solo si firma un apoderado o una persona moral.', FALSE, NULL, 'POWER_OF_ATTORNEY', 40),
    (gen_random_uuid(), 'OPERATION', 'SELLER', 'SALE', NULL, 'Escritura o título de propiedad', NULL, TRUE, NULL, 'DEED', 50),
    (gen_random_uuid(), 'OPERATION', 'SELLER', 'SALE', NULL, 'Certificado de libertad de gravámenes', NULL, FALSE, 30, 'LIEN_CERTIFICATE', 60),
    (gen_random_uuid(), 'OPERATION', 'SELLER', 'SALE', NULL, 'Predial al corriente', NULL, FALSE, NULL, 'PROPERTY_TAX', 70),
    -- Documentación de la operación: comprador / arrendatario
    (gen_random_uuid(), 'OPERATION', 'BUYER', NULL, NULL, 'Identificación oficial vigente', 'INE o pasaporte y, en su caso, de su representante.', TRUE, NULL, 'INE', 110),
    (gen_random_uuid(), 'OPERATION', 'BUYER', NULL, NULL, 'Comprobante de domicilio', 'No mayor a 3 meses.', FALSE, 90, 'PROOF_OF_ADDRESS', 120),
    (gen_random_uuid(), 'OPERATION', 'BUYER', NULL, NULL, 'Constancia de situación fiscal', NULL, FALSE, NULL, 'TAX_STATUS_CERTIFICATE', 130),
    (gen_random_uuid(), 'OPERATION', 'BUYER', NULL, NULL, 'Poder o acta constitutiva del representante', 'Solo si firma un apoderado o una persona moral.', FALSE, NULL, 'POWER_OF_ATTORNEY', 140),
    -- Documentación de la operación: de la transacción
    (gen_random_uuid(), 'OPERATION', NULL, 'SALE', NULL, 'Contrato de la operación firmado', 'Promesa o contrato de compraventa firmado por las partes.', TRUE, NULL, NULL, 200),
    (gen_random_uuid(), 'OPERATION', NULL, 'RENT', NULL, 'Contrato de arrendamiento firmado', NULL, TRUE, NULL, 'LEASE_AGREEMENT', 200),
    (gen_random_uuid(), 'OPERATION', NULL, NULL, NULL, 'Evidencia del cierre', 'Lo que acredita el cierre comercial (p. ej. firma ante notario, entrega del inmueble).', TRUE, NULL, NULL, 210),
    (gen_random_uuid(), 'OPERATION', NULL, NULL, NULL, 'Forma de pago y comprobantes', NULL, FALSE, NULL, NULL, 220),
    (gen_random_uuid(), 'OPERATION', NULL, NULL, 'EXTERNAL', 'Acuerdo de participación con la oficina o tercero', 'Alcance de la intervención de C21 Genera y reparto de comisión.', FALSE, NULL, NULL, 230),
    -- PLD
    (gen_random_uuid(), 'PLD', 'SELLER', NULL, NULL, 'Expediente de identificación del cliente', 'Datos y documentos de identificación conforme a la matriz aprobada.', TRUE, NULL, NULL, 300),
    (gen_random_uuid(), 'PLD', 'SELLER', NULL, NULL, 'Declaración de beneficiario controlador', NULL, FALSE, NULL, NULL, 310),
    (gen_random_uuid(), 'PLD', 'BUYER', NULL, NULL, 'Expediente de identificación del cliente', 'Datos y documentos de identificación conforme a la matriz aprobada.', TRUE, NULL, NULL, 320),
    (gen_random_uuid(), 'PLD', 'BUYER', NULL, NULL, 'Declaración de beneficiario controlador', NULL, FALSE, NULL, NULL, 330),
    (gen_random_uuid(), 'PLD', 'BUYER', NULL, NULL, 'Declaración del origen de los recursos', NULL, FALSE, NULL, NULL, 340),
    (gen_random_uuid(), 'PLD', NULL, NULL, NULL, 'Consulta en listas de personas bloqueadas', 'Evidencia de la consulta de ambas partes.', FALSE, NULL, NULL, 350),
    (gen_random_uuid(), 'PLD', NULL, NULL, NULL, 'Determinación de umbrales de identificación y aviso', 'Si aplica, acuse del aviso presentado.', TRUE, NULL, NULL, 360),
    -- PROFECO
    (gen_random_uuid(), 'PROFECO', NULL, NULL, 'OWN', 'Contrato de adhesión registrado usado con el cliente', 'Número de registro ante PROFECO del contrato firmado con el cliente.', FALSE, NULL, 'SIGNED_CONTRACT', 400),
    (gen_random_uuid(), 'PROFECO', NULL, NULL, NULL, 'Aviso de privacidad entregado', NULL, FALSE, NULL, 'PRIVACY_NOTICE', 410),
    (gen_random_uuid(), 'PROFECO', NULL, NULL, NULL, 'Información por escrito del precio total y la comisión', NULL, FALSE, NULL, NULL, 420),
    (gen_random_uuid(), 'PROFECO', NULL, NULL, NULL, 'Comprobante del cobro de la comisión', NULL, FALSE, NULL, NULL, 430);

-- Requisito aplicado a una operación (copia del renglón de la matriz al iniciar el cierre).
CREATE TABLE closing_requirement (
    id                       UUID PRIMARY KEY,
    operation_id             UUID NOT NULL REFERENCES closing_operation (id) ON DELETE CASCADE,
    template_id              UUID,
    section                  VARCHAR(16) NOT NULL,
    party_side               VARCHAR(16),
    label                    VARCHAR(240) NOT NULL,
    description              TEXT,
    mandatory                BOOLEAN NOT NULL,
    validity_days            INTEGER,
    document_type_hint       VARCHAR(40),
    sort_order               INTEGER NOT NULL,
    status                   VARCHAR(24) NOT NULL,         -- PENDING | RECEIVED | IN_REVIEW | VALIDATED | NEEDS_CORRECTION | NOT_APPLICABLE
    requested_from           VARCHAR(24),                  -- CLIENT | COUNTERPARTY | OFFICE | THIRD_PARTY | INTERNAL
    requested_at             TIMESTAMPTZ,
    follow_up_owner          VARCHAR(160),
    due_date                 DATE,
    observation              TEXT,
    reviewed_by_name         VARCHAR(160),
    reviewed_at              TIMESTAMPTZ,
    na_justification         TEXT,
    na_requested_by_name     VARCHAR(160),
    na_requested_at          TIMESTAMPTZ,
    na_approved_by_name      VARCHAR(160),
    na_approved_at           TIMESTAMPTZ,
    version                  BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_closing_requirement_operation ON closing_requirement (operation_id, sort_order);

-- Evidencias de cada requisito: cada carga o vínculo es una versión nueva; las anteriores se conservan.
CREATE TABLE closing_requirement_evidence (
    id                    UUID PRIMARY KEY,
    requirement_id        UUID NOT NULL REFERENCES closing_requirement (id) ON DELETE CASCADE,
    version_number        INTEGER NOT NULL,
    kind                  VARCHAR(24) NOT NULL,            -- FILE | EXPEDIENTE_DOCUMENT | NOTE
    storage_key           TEXT,
    filename              VARCHAR(255),
    content_type          VARCHAR(80),
    size_bytes            BIGINT,
    sha256                VARCHAR(64),
    document_id           UUID,
    document_version_id   UUID,
    document_name         VARCHAR(255),
    note                  TEXT,
    document_date         DATE,                            -- fecha del documento, para su vigencia
    attached_by_name      VARCHAR(160),
    attached_at           TIMESTAMPTZ NOT NULL,
    UNIQUE (requirement_id, version_number)
);

-- Historial: cambios de estado, revisiones, observaciones, autorizaciones y reaperturas.
CREATE TABLE closing_operation_event (
    id               UUID PRIMARY KEY,
    operation_id     UUID NOT NULL REFERENCES closing_operation (id) ON DELETE CASCADE,
    requirement_id   UUID,
    type             VARCHAR(40) NOT NULL,
    detail           TEXT,
    actor_user_id    UUID,
    actor_name       VARCHAR(160),
    occurred_at      TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_closing_operation_event ON closing_operation_event (operation_id, occurred_at DESC);

INSERT INTO permission (code, description) VALUES
    ('OPERATION_VIEW_ALL', 'Ver todas las operaciones de cierre (sin esto, solo las propias)'),
    ('OPERATION_MANAGE', 'Registrar operaciones de cierre (propias y externas), sus partes y evidencias'),
    ('OPERATION_REVIEW', 'Validar requisitos de cierre, aprobar "No aplica", autorizar el expediente completo y reabrir operaciones'),
    ('OPERATION_MATRIX_MANAGE', 'Configurar y aprobar la matriz documental de cierre (área legal o cumplimiento)');

INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, 'OPERATION_MANAGE' FROM app_role r WHERE r.code IN ('ADMINISTRATOR', 'DIRECTOR', 'ADVISOR');

INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, 'OPERATION_VIEW_ALL' FROM app_role r WHERE r.code IN ('ADMINISTRATOR', 'DIRECTOR');

INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, p.code
  FROM app_role r
  CROSS JOIN permission p
 WHERE r.code = 'ADMINISTRATOR'
   AND p.code IN ('OPERATION_REVIEW', 'OPERATION_MATRIX_MANAGE');
