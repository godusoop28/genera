-- Módulo 3 (CRM): leads, ruteo de asesores y asistente de WhatsApp.
-- Las propiedades NO se guardan aquí: se consultan en 21Online (Módulo 2
-- será el dueño del inventario). Un lead solo conserva la referencia y una
-- foto mínima (clave/título) para mostrarla aunque 21Online no responda.

-- ---------------------------------------------------------------------------
-- Permisos
-- ---------------------------------------------------------------------------
INSERT INTO permission (code, description) VALUES
    ('LEAD_VIEW_ALL', 'Consultar todos los leads y el dashboard del CRM'),
    ('LEAD_VIEW_OWN', 'Consultar los leads asignados a uno mismo'),
    ('LEAD_MANAGE', 'Cambiar la etapa y agregar notas a los leads que puede ver'),
    ('LEAD_ASSIGN', 'Asignar y reasignar leads a otro asesor'),
    ('ADVISOR_ROUTING_MANAGE', 'Configurar qué asesores reciben leads, su peso, límite y horario'),
    ('CONVERSATION_VIEW', 'Consultar las conversaciones del asistente de WhatsApp'),
    ('CONVERSATION_MANAGE', 'Pausar o reanudar la IA y responder como equipo en WhatsApp');

-- Administrador y Director: todo el CRM.
INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, p.code
  FROM app_role r
  CROSS JOIN permission p
 WHERE r.code IN ('ADMINISTRATOR', 'DIRECTOR')
   AND p.code IN ('LEAD_VIEW_ALL', 'LEAD_VIEW_OWN', 'LEAD_MANAGE', 'LEAD_ASSIGN', 'ADVISOR_ROUTING_MANAGE', 'CONVERSATION_VIEW', 'CONVERSATION_MANAGE');

-- Asesor: solo sus leads.
INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, p.code
  FROM app_role r
  CROSS JOIN permission p
 WHERE r.code = 'ADVISOR'
   AND p.code IN ('LEAD_VIEW_OWN', 'LEAD_MANAGE');

-- ---------------------------------------------------------------------------
-- Ruteo de asesores: quién recibe leads y cuántos.
-- ---------------------------------------------------------------------------
CREATE TABLE crm_advisor_profile (
    user_id            UUID PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    receives_leads     BOOLEAN NOT NULL DEFAULT FALSE,
    weight             INTEGER NOT NULL DEFAULT 5 CHECK (weight BETWEEN 0 AND 100),
    daily_limit        INTEGER CHECK (daily_limit IS NULL OR daily_limit > 0),
    -- Horario (hora de Ciudad de México) en que entra a la ruleta; vacío = todo el día.
    shift_start        TIME,
    shift_end          TIME,
    paused_until       TIMESTAMPTZ,
    -- Id del usuario en 21Online: con él se sabe de quién es cada propiedad.
    online21_user_id   BIGINT UNIQUE,
    -- WhatsApp del asesor (E.164) para el aviso de lead nuevo.
    whatsapp_phone     VARCHAR(20),
    version            BIGINT NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL
);

-- ---------------------------------------------------------------------------
-- Leads
-- ---------------------------------------------------------------------------
CREATE TABLE lead (
    id                     UUID PRIMARY KEY,
    name                   TEXT NOT NULL,
    phone                  VARCHAR(32),
    -- Solo dígitos: sirve para encontrar al mismo contacto aunque cambie el formato.
    phone_digits           VARCHAR(20),
    email                  VARCHAR(255),
    source                 VARCHAR(24) NOT NULL,
    source_detail          TEXT,
    interest               VARCHAR(16) NOT NULL,
    stage                  VARCHAR(16) NOT NULL,
    -- Propiedad de 21Online (id numérico) y foto mínima para mostrarla.
    property_online21_id   BIGINT,
    property_key           VARCHAR(64),
    property_title         TEXT,
    advisor_user_id        UUID REFERENCES app_user (id),
    assignment_method      VARCHAR(16),
    assigned_at            TIMESTAMPTZ,
    summary                TEXT,
    budget                 NUMERIC(14, 2),
    lost_reason            TEXT,
    conversation_id        UUID,
    -- Llave de idempotencia (p. ej. "wa:<conversación>:s<sesión>"): un reintento no duplica el lead.
    request_key            VARCHAR(120) UNIQUE,
    -- Registro del prospecto en 21Online (opcional, ver Online21Properties.pushProspects).
    online21_prospect_id   BIGINT,
    online21_sync_status   VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUIRED',
    advisor_notice_status  VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUIRED',
    last_activity_at       TIMESTAMPTZ NOT NULL,
    version                BIGINT NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ NOT NULL,
    updated_at             TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_lead_created ON lead (created_at DESC);
CREATE INDEX idx_lead_advisor ON lead (advisor_user_id, created_at DESC);
CREATE INDEX idx_lead_stage ON lead (stage);
CREATE INDEX idx_lead_phone ON lead (phone_digits, created_at DESC);
CREATE INDEX idx_lead_property ON lead (property_online21_id);

CREATE TABLE lead_activity (
    id             UUID PRIMARY KEY,
    lead_id        UUID NOT NULL REFERENCES lead (id) ON DELETE CASCADE,
    kind           VARCHAR(16) NOT NULL,
    text           TEXT NOT NULL,
    actor_user_id  UUID,
    actor_name     TEXT,
    created_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_lead_activity_lead ON lead_activity (lead_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- Asistente de WhatsApp (misma lógica que el panel de Inova).
-- ---------------------------------------------------------------------------
CREATE TABLE wa_conversation (
    id                   UUID PRIMARY KEY,
    -- Número del cliente tal como lo reporta WhatsApp (wa_id, solo dígitos).
    wa_id                VARCHAR(20) NOT NULL UNIQUE,
    contact_name         TEXT,
    -- Conversación del simulador interno: nunca envía por WhatsApp ni crea leads reales.
    test                 BOOLEAN NOT NULL DEFAULT FALSE,
    control              VARCHAR(16) NOT NULL DEFAULT 'AI',
    primary_intent       VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    secondary_intents    TEXT NOT NULL DEFAULT '',
    facts_json           TEXT NOT NULL DEFAULT '{}',
    properties_json      TEXT NOT NULL DEFAULT '[]',
    summary              TEXT,
    clarification_count  INTEGER NOT NULL DEFAULT 0,
    handoff_state        VARCHAR(24) NOT NULL DEFAULT 'NONE',
    handoff_at           TIMESTAMPTZ,
    lead_id              UUID,
    session_seq          INTEGER NOT NULL DEFAULT 1,
    session_start_seq    BIGINT NOT NULL DEFAULT 0,
    previous_context     TEXT,
    last_seq             BIGINT NOT NULL DEFAULT 0,
    processed_seq        BIGINT NOT NULL DEFAULT 0,
    burst_started_at     TIMESTAMPTZ,
    process_after        TIMESTAMPTZ,
    failed_attempts      INTEGER NOT NULL DEFAULT 0,
    lease_owner          VARCHAR(64),
    lease_until          TIMESTAMPTZ,
    needs_attention      TEXT,
    last_inbound_at      TIMESTAMPTZ,
    last_message_at      TIMESTAMPTZ,
    version              BIGINT NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_wa_conversation_due ON wa_conversation (process_after) WHERE process_after IS NOT NULL;
CREATE INDEX idx_wa_conversation_recent ON wa_conversation (last_message_at DESC);

CREATE TABLE wa_message (
    id               UUID PRIMARY KEY,
    conversation_id  UUID NOT NULL REFERENCES wa_conversation (id) ON DELETE CASCADE,
    seq              BIGINT NOT NULL,
    role             VARCHAR(16) NOT NULL,
    text             TEXT NOT NULL,
    -- Id del mensaje en WhatsApp: deduplica reentregas del webhook.
    external_id      VARCHAR(128) UNIQUE,
    status           VARCHAR(16) NOT NULL,
    error            TEXT,
    attempts         INTEGER NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL,
    sent_at          TIMESTAMPTZ,
    UNIQUE (conversation_id, seq)
);

CREATE INDEX idx_wa_message_outbox ON wa_message (status, created_at) WHERE status IN ('QUEUED', 'SENDING');

CREATE TABLE wa_turn (
    id               UUID PRIMARY KEY,
    conversation_id  UUID NOT NULL REFERENCES wa_conversation (id) ON DELETE CASCADE,
    from_seq         BIGINT NOT NULL,
    to_seq           BIGINT NOT NULL,
    status           VARCHAR(16) NOT NULL,
    model            VARCHAR(64),
    input_tokens     INTEGER,
    output_tokens    INTEGER,
    tool_trace       TEXT,
    error            TEXT,
    duration_ms      INTEGER,
    created_at       TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_wa_turn_conversation ON wa_turn (conversation_id, created_at DESC);

CREATE TABLE wa_escalation (
    id               UUID PRIMARY KEY,
    conversation_id  UUID NOT NULL REFERENCES wa_conversation (id) ON DELETE CASCADE,
    type             VARCHAR(24) NOT NULL,
    reason           TEXT NOT NULL,
    status           VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    created_at       TIMESTAMPTZ NOT NULL,
    resolved_at      TIMESTAMPTZ
);

CREATE INDEX idx_wa_escalation_open ON wa_escalation (status, created_at DESC);
