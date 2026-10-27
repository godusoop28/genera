-- Control de Cambios #28: encuesta de satisfacción NPS por punto de contacto.

-- Configuración de cada uno de los 17 puntos de contacto (10 vía sistema y 7
-- vía asesor). delay_minutes: 0 inmediato, 30, 120, 1440 (24 h); la de
-- seguimiento post-venta se manda 30 días después del cierre.
CREATE TABLE survey_touchpoint_config (
    touchpoint       VARCHAR(32) PRIMARY KEY,
    active           BOOLEAN NOT NULL,
    channel          VARCHAR(16) NOT NULL,
    delay_minutes    INTEGER NOT NULL DEFAULT 0,
    ask_comment      BOOLEAN NOT NULL DEFAULT TRUE,
    intro_message    TEXT,
    updated_by_name  TEXT,
    updated_at       TIMESTAMPTZ NOT NULL
);

-- Puntos vía sistema: por WhatsApp (canal principal), activos los momentos clave.
-- Puntos vía asesor: los activa el asesor generando la liga (canal ADVISOR_LINK).
INSERT INTO survey_touchpoint_config (touchpoint, active, channel, delay_minutes, ask_comment, updated_at) VALUES
    ('REGISTRATION',        FALSE, 'WHATSAPP',     120,   TRUE, now()),
    ('DOCUMENT_PREP',       FALSE, 'SYSTEM',       0,     TRUE, now()),
    ('DOCUMENTS_UPLOADED',  TRUE,  'WHATSAPP',     120,   TRUE, now()),
    ('PRIVACY_SIGNED',      FALSE, 'SYSTEM',       0,     TRUE, now()),
    ('AI_VALIDATION',       FALSE, 'WHATSAPP',     120,   TRUE, now()),
    ('DOCUMENT_UPDATE',     FALSE, 'WHATSAPP',     1440,  TRUE, now()),
    ('CONTRACT_SENT',       FALSE, 'WHATSAPP',     1440,  TRUE, now()),
    ('CONTRACT_SIGNED',     TRUE,  'WHATSAPP',     120,   TRUE, now()),
    ('NOTARY_SENT',         FALSE, 'WHATSAPP',     1440,  TRUE, now()),
    ('CLOSING',             TRUE,  'WHATSAPP',     1440,  TRUE, now()),
    ('FIRST_VISIT',         TRUE,  'ADVISOR_LINK', 0,     TRUE, now()),
    ('PROPOSAL',            TRUE,  'ADVISOR_LINK', 0,     TRUE, now()),
    ('NEGOTIATION',         TRUE,  'ADVISOR_LINK', 0,     TRUE, now()),
    ('IN_PERSON_SIGNING',   TRUE,  'ADVISOR_LINK', 0,     TRUE, now()),
    ('KEY_HANDOVER',        TRUE,  'ADVISOR_LINK', 0,     TRUE, now()),
    ('ISSUE_RESOLVED',      TRUE,  'ADVISOR_LINK', 0,     TRUE, now()),
    ('POST_SALE',           TRUE,  'WHATSAPP',     43200, TRUE, now());

-- Reglas generales (una sola fila): anti-saturación, recordatorio, vigencia y
-- si cada asesor ve su propio NPS.
CREATE TABLE survey_settings (
    id                     SMALLINT PRIMARY KEY,
    min_days_between       INTEGER NOT NULL DEFAULT 7,
    max_per_period         INTEGER NOT NULL DEFAULT 3,
    period_days            INTEGER NOT NULL DEFAULT 30,
    reminder_enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    reminder_after_hours   INTEGER NOT NULL DEFAULT 48,
    max_reminders          INTEGER NOT NULL DEFAULT 1,
    expire_days            INTEGER NOT NULL DEFAULT 14,
    advisors_see_own       BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by_name        TEXT,
    updated_at             TIMESTAMPTZ NOT NULL
);
INSERT INTO survey_settings (id, updated_at) VALUES (1, now());

-- Cada encuesta enviada (o programada) y su respuesta. token: va en la liga
-- pública /encuesta/<token>; client_*: foto de los datos de contacto al crearla.
CREATE TABLE nps_survey (
    id                 UUID PRIMARY KEY,
    token              VARCHAR(64) NOT NULL UNIQUE,
    touchpoint         VARCHAR(32) NOT NULL,
    channel            VARCHAR(16) NOT NULL,
    status             VARCHAR(12) NOT NULL,
    expediente_id      UUID REFERENCES expediente (id) ON DELETE CASCADE,
    expediente_folio   VARCHAR(40),
    lead_id            UUID REFERENCES lead (id) ON DELETE SET NULL,
    client_name        TEXT,
    client_phone       VARCHAR(32),
    client_email       VARCHAR(255),
    contact_key        VARCHAR(120),
    advisor_user_id    UUID,
    advisor_name       TEXT,
    intro_message      TEXT,
    ask_comment        BOOLEAN NOT NULL DEFAULT TRUE,
    send_at            TIMESTAMPTZ NOT NULL,
    sent_at            TIMESTAMPTZ,
    reminders_sent     INTEGER NOT NULL DEFAULT 0,
    last_reminder_at   TIMESTAMPTZ,
    delivery_detail    TEXT,
    score              INTEGER,
    comment            TEXT,
    answered_at        TIMESTAMPTZ,
    created_by_name    TEXT,
    created_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_nps_score CHECK (score IS NULL OR (score BETWEEN 0 AND 10))
);

CREATE INDEX idx_nps_survey_expediente ON nps_survey (expediente_id, created_at DESC);
CREATE INDEX idx_nps_survey_lead ON nps_survey (lead_id, created_at DESC);
CREATE INDEX idx_nps_survey_due ON nps_survey (status, send_at);
CREATE INDEX idx_nps_survey_contact ON nps_survey (contact_key, send_at);
CREATE INDEX idx_nps_survey_answered ON nps_survey (answered_at DESC);

INSERT INTO permission (code, description) VALUES
    ('NPS_VIEW', 'Consultar el dashboard y los resultados de satisfacción (NPS) de todos los asesores'),
    ('NPS_MANAGE', 'Configurar las encuestas NPS por punto de contacto');

INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, p.code
  FROM app_role r
  CROSS JOIN permission p
 WHERE r.code IN ('ADMINISTRATOR', 'DIRECTOR')
   AND p.code IN ('NPS_VIEW', 'NPS_MANAGE');
