-- Controles de Cambios #29 y #30: catálogo de fuentes de lead administrable
-- y rediseño del seguimiento (ficha completa, pipeline de 10 etapas con
-- historial, actividades programadas/realizadas, inmuebles compartidos y
-- vínculo con el expediente).

-- ---------------------------------------------------------------------------
-- CC#29: catálogo de fuentes. channel agrupa la fuente en el canal general
-- (LeadSource) que ya usan el bot, los filtros y los reportes anteriores.
-- Desactivar una fuente solo la oculta del selector: los leads que la usaron
-- conservan su código y su nombre.
-- ---------------------------------------------------------------------------
CREATE TABLE lead_source_option (
    code            VARCHAR(48) PRIMARY KEY,
    category        VARCHAR(24) NOT NULL,
    label           VARCHAR(80) NOT NULL,
    description     TEXT,
    channel         VARCHAR(24) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    -- "Otra — especificar": exige describir el origen.
    requires_detail BOOLEAN NOT NULL DEFAULT FALSE,
    -- Solo la asigna el sistema (p. ej. el asistente de WhatsApp): no aparece en el alta manual.
    system          BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO lead_source_option (code, category, label, description, channel, active, requires_detail, system, sort_order) VALUES
    ('INMUEBLES24',        'PORTALS',      'Inmuebles24',               'Principal portal inmobiliario de México', 'PORTAL', TRUE, FALSE, FALSE, 10),
    ('LAMUDI',             'PORTALS',      'Lamudi',                    'Portal inmobiliario con presencia en México', 'PORTAL', TRUE, FALSE, FALSE, 20),
    ('VIVANUNCIOS',        'PORTALS',      'Vivanuncios',               'Avisos clasificados con sección inmobiliaria', 'PORTAL', TRUE, FALSE, FALSE, 30),
    ('PROPIEDADES_COM',    'PORTALS',      'Propiedades.com',           'Portal inmobiliario nacional', 'PORTAL', TRUE, FALSE, FALSE, 40),
    ('ENCUENTRA24',        'PORTALS',      'Encuentra24',               'Portal regional en Latinoamérica', 'PORTAL', TRUE, FALSE, FALSE, 50),
    ('MERCADOLIBRE',       'PORTALS',      'MercadoLibre Inmuebles',    'Bienes raíces de MercadoLibre', 'PORTAL', TRUE, FALSE, FALSE, 60),
    ('HOMIE',              'PORTALS',      'Homie',                     'Plataforma de arrendamiento digital', 'PORTAL', TRUE, FALSE, FALSE, 70),
    ('CENTURY21_MX',       'PORTALS',      'Century21.com.mx',          'Portal propio de la franquicia', 'PORTAL', TRUE, FALSE, FALSE, 80),
    ('LEGACY_PORTAL',      'PORTALS',      'Portal (sin especificar)',  'Registros anteriores al catálogo', 'PORTAL', FALSE, FALSE, FALSE, 90),

    ('WHATSAPP',           'SOCIAL',       'WhatsApp',                  'Contacto directo al asesor o al número de la agencia', 'WHATSAPP', TRUE, FALSE, FALSE, 10),
    ('WHATSAPP_BOT',       'SOCIAL',       'WhatsApp (asistente)',      'Lo registró el asistente de WhatsApp', 'WHATSAPP_BOT', TRUE, FALSE, TRUE, 15),
    ('FACEBOOK_AD',        'SOCIAL',       'Facebook — Anuncio',        'Anuncio pagado en Facebook/Instagram', 'SOCIAL', TRUE, FALSE, FALSE, 20),
    ('FACEBOOK_ORGANIC',   'SOCIAL',       'Facebook — Publicación orgánica', 'Publicación sin inversión publicitaria', 'SOCIAL', TRUE, FALSE, FALSE, 30),
    ('INSTAGRAM',          'SOCIAL',       'Instagram',                 'Mensaje directo o historia', 'SOCIAL', TRUE, FALSE, FALSE, 40),
    ('TIKTOK',             'SOCIAL',       'TikTok',                    'Contenido o anuncio en TikTok', 'SOCIAL', TRUE, FALSE, FALSE, 50),
    ('LINKEDIN',           'SOCIAL',       'LinkedIn',                  'Red profesional (comercial/industrial)', 'SOCIAL', TRUE, FALSE, FALSE, 60),
    ('YOUTUBE',            'SOCIAL',       'YouTube',                   'Video de propiedad o canal de la agencia', 'SOCIAL', TRUE, FALSE, FALSE, 70),

    ('GOOGLE_ORGANIC',     'SEARCH',       'Google — Búsqueda orgánica', 'Resultados naturales de Google', 'WEBSITE', TRUE, FALSE, FALSE, 10),
    ('GOOGLE_ADS',         'SEARCH',       'Google — Anuncio (SEM)',    'Google Ads', 'CAMPAIGN', TRUE, FALSE, FALSE, 20),
    ('WEBSITE',            'SEARCH',       'Sitio web propio',          'Página web de C21 Genera', 'WEBSITE', TRUE, FALSE, FALSE, 30),
    ('EMAIL',              'SEARCH',       'Email / Boletín',           'Correo electrónico o newsletter', 'CAMPAIGN', TRUE, FALSE, FALSE, 40),
    ('LEGACY_CAMPAIGN',    'SEARCH',       'Campaña (sin especificar)', 'Registros anteriores al catálogo', 'CAMPAIGN', FALSE, FALSE, FALSE, 90),

    ('LONA',               'OUTDOOR',      'Lona',                      'Lonas, banners o mamparas en la propiedad o vía pública', 'OUTDOOR', TRUE, FALSE, FALSE, 10),
    ('FLYER',              'OUTDOOR',      'Volante / Flyer',           'Material impreso en zonas de interés', 'OUTDOOR', TRUE, FALSE, FALSE, 20),
    ('BILLBOARD',          'OUTDOOR',      'Espectacular',              'Espectacular o valla publicitaria', 'OUTDOOR', TRUE, FALSE, FALSE, 30),
    ('PRINT',              'OUTDOOR',      'Periódico / Revista',       'Medio impreso local o especializado', 'OUTDOOR', TRUE, FALSE, FALSE, 40),
    ('DIRECTORY',          'OUTDOOR',      'Directorio físico',         'Directorios impresos del sector', 'OUTDOOR', TRUE, FALSE, FALSE, 50),

    ('REFERRAL_CLIENT',    'REFERRALS',    'Referido de cliente',       'Recomendado por un cliente anterior', 'REFERRAL', TRUE, FALSE, FALSE, 10),
    ('REFERRAL_ADVISOR',   'REFERRALS',    'Referido de asesor',        'Compartido entre asesores de la agencia', 'REFERRAL', TRUE, FALSE, FALSE, 20),
    ('REFERRAL_NOTARY',    'REFERRALS',    'Referido de notaría',       'Enviado por la notaría', 'REFERRAL', TRUE, FALSE, FALSE, 30),
    ('REFERRAL_BANK',      'REFERRALS',    'Referido de banco / crédito', 'Institución financiera o promotor hipotecario', 'REFERRAL', TRUE, FALSE, FALSE, 40),
    ('CENTURY21_NETWORK',  'REFERRALS',    'Red Century 21',            'Otra oficina de la franquicia', 'REFERRAL', TRUE, FALSE, FALSE, 50),
    ('PERSONAL_NETWORK',   'REFERRALS',    'Relación personal del asesor', 'Red personal del asesor', 'REFERRAL', TRUE, FALSE, FALSE, 60),
    ('LEGACY_REFERRAL',    'REFERRALS',    'Referido (sin especificar)', 'Registros anteriores al catálogo', 'REFERRAL', FALSE, FALSE, FALSE, 90),

    ('WALK_IN',            'IN_PERSON',    'Visita en oficina',         'Llegó directamente a la oficina', 'WALK_IN', TRUE, FALSE, FALSE, 10),
    ('EXPO',               'IN_PERSON',    'Expo inmobiliaria',         'Feria o exposición del sector', 'EVENT', TRUE, FALSE, FALSE, 20),
    ('OPEN_HOUSE',         'IN_PERSON',    'Open house',                'Jornada de puertas abiertas', 'EVENT', TRUE, FALSE, FALSE, 30),
    ('PHONE',              'IN_PERSON',    'Llamada telefónica directa', 'Llamó a la agencia o al asesor', 'PHONE', TRUE, FALSE, FALSE, 40),

    ('INTERNAL_DATABASE',  'REACTIVATION', 'Base de datos interna',     'Prospecto anterior recuperado', 'REACTIVATION', TRUE, FALSE, FALSE, 10),
    ('REACTIVATION_CAMPAIGN', 'REACTIVATION', 'CRM / Campaña de reactivación', 'Recontactado por campaña automatizada', 'REACTIVATION', TRUE, FALSE, FALSE, 20),

    ('UNKNOWN',            'UNKNOWN',      'Desconocida',               'El cliente no recuerda o no se identificó el origen', 'OTHER', TRUE, FALSE, FALSE, 10),
    ('OTHER',              'UNKNOWN',      'Otra — especificar',        'Origen no contemplado; requiere descripción', 'OTHER', TRUE, TRUE, FALSE, 20);

ALTER TABLE lead
    ADD COLUMN source_code          VARCHAR(48) REFERENCES lead_source_option (code),
    -- CC#30: ficha del prospecto.
    ADD COLUMN first_contact_on     DATE,
    ADD COLUMN property_category_id UUID,
    ADD COLUMN property_subtype_id  UUID,
    ADD COLUMN property_type_label  VARCHAR(160),
    ADD COLUMN zone                 VARCHAR(200),
    ADD COLUMN budget_min           NUMERIC(14, 2),
    ADD COLUMN desired_features     TEXT,
    ADD COLUMN temperature          VARCHAR(8),
    ADD COLUMN close_probability    INTEGER,
    ADD COLUMN expected_close_on    DATE,
    ADD COLUMN lost_reason_code     VARCHAR(16),
    ADD COLUMN stage_entered_at     TIMESTAMPTZ,
    -- CC#30 parte 7: expediente del inmueble al llegar a "Oferta aceptada".
    ADD COLUMN expediente_id        UUID REFERENCES expediente (id) ON DELETE SET NULL,
    ADD COLUMN expediente_folio     VARCHAR(40);

UPDATE lead SET source_code = CASE source
    WHEN 'WHATSAPP_BOT' THEN 'WHATSAPP_BOT'
    WHEN 'PORTAL' THEN 'LEGACY_PORTAL'
    WHEN 'CAMPAIGN' THEN 'LEGACY_CAMPAIGN'
    WHEN 'WEBSITE' THEN 'WEBSITE'
    WHEN 'REFERRAL' THEN 'LEGACY_REFERRAL'
    WHEN 'WALK_IN' THEN 'WALK_IN'
    ELSE 'PHONE' END;
UPDATE lead SET first_contact_on = (created_at AT TIME ZONE 'America/Mexico_City')::date, stage_entered_at = created_at;
-- "Cerrado ganado" ahora se llama "Cierre"; el resto de las etapas conserva su código.

CREATE INDEX idx_lead_source_code ON lead (source_code);
CREATE INDEX idx_lead_expediente ON lead (expediente_id);

-- Entrada y salida de cada etapa (CC#30: tiempo promedio por etapa).
CREATE TABLE lead_stage_history (
    id          UUID PRIMARY KEY,
    lead_id     UUID NOT NULL REFERENCES lead (id) ON DELETE CASCADE,
    stage       VARCHAR(16) NOT NULL,
    entered_at  TIMESTAMPTZ NOT NULL,
    exited_at   TIMESTAMPTZ,
    actor_name  TEXT
);
CREATE INDEX idx_lead_stage_history ON lead_stage_history (lead_id, entered_at);

INSERT INTO lead_stage_history (id, lead_id, stage, entered_at, exited_at, actor_name)
SELECT md5(random()::text || id::text)::uuid, id, stage, created_at, NULL, NULL FROM lead;

-- Actividades del lead: programadas (futuras) y realizadas (con resultado).
CREATE TABLE lead_task (
    id                    UUID PRIMARY KEY,
    lead_id               UUID NOT NULL REFERENCES lead (id) ON DELETE CASCADE,
    type                  VARCHAR(16) NOT NULL,
    status                VARCHAR(12) NOT NULL,
    title                 VARCHAR(200),
    scheduled_at          TIMESTAMPTZ,
    duration_minutes      INTEGER,
    advisor_user_id       UUID REFERENCES app_user (id),
    property_key          VARCHAR(64),
    property_title        TEXT,
    notes_before          TEXT,
    reminder_minutes      INTEGER,
    reminder_sent_at      TIMESTAMPTZ,
    overdue_notified_at   TIMESTAMPTZ,
    result                VARCHAR(16),
    actual_minutes        INTEGER,
    outcome_notes         TEXT,
    next_action           TEXT,
    next_contact_at       TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ,
    created_by_user_id    UUID,
    created_by_name       TEXT,
    version               BIGINT NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_lead_task_lead ON lead_task (lead_id, scheduled_at);
CREATE INDEX idx_lead_task_agenda ON lead_task (status, scheduled_at);
CREATE INDEX idx_lead_task_advisor ON lead_task (advisor_user_id, scheduled_at);

-- Inmuebles mostrados o propuestos al prospecto.
CREATE TABLE lead_shared_property (
    id                    UUID PRIMARY KEY,
    lead_id               UUID NOT NULL REFERENCES lead (id) ON DELETE CASCADE,
    property_online21_id  BIGINT,
    property_key          VARCHAR(64),
    property_title        TEXT,
    note                  TEXT,
    actor_name            TEXT,
    created_at            TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_lead_shared_property ON lead_shared_property (lead_id, created_at);

-- Alertas y recordatorios del seguimiento.
ALTER TABLE crm_assignment_settings
    ADD COLUMN stale_days INTEGER NOT NULL DEFAULT 7,
    ADD COLUMN reminder_email BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO permission (code, description) VALUES
    ('LEAD_SOURCE_MANAGE', 'Activar, desactivar y agregar fuentes de lead');

INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, 'LEAD_SOURCE_MANAGE' FROM app_role r WHERE r.code IN ('ADMINISTRATOR', 'DIRECTOR');
