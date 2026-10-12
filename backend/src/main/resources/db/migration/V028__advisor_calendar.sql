-- Calendario de turnos, guardias, ausencias y rondas de asignación (Módulo 3).
-- El calendario pasa a ser la ÚNICA fuente de disponibilidad para asignar
-- leads: el horario simple anterior (shift_start/shift_end) se conserva como
-- texto de referencia y esos asesores quedan marcados para completar su
-- calendario. No se inventan días de trabajo.

-- ---------------------------------------------------------------------------
-- Permisos
-- ---------------------------------------------------------------------------
INSERT INTO permission (code, description) VALUES
    ('CALENDAR_VIEW_ALL', 'Consultar el calendario de turnos de todos los asesores'),
    ('CALENDAR_VIEW_OWN', 'Consultar el propio horario de turnos'),
    ('CALENDAR_MANAGE', 'Crear, editar y eliminar turnos y ausencias de cualquier asesor'),
    ('CALENDAR_EDIT_OWN', 'Editar los propios turnos y ausencias');

INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, p.code
  FROM app_role r
  CROSS JOIN permission p
 WHERE r.code IN ('ADMINISTRATOR', 'DIRECTOR')
   AND p.code IN ('CALENDAR_VIEW_ALL', 'CALENDAR_VIEW_OWN', 'CALENDAR_MANAGE');

-- Asesor: solo consulta su horario. Editarlo requiere asignarle CALENDAR_EDIT_OWN explícitamente.
INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, 'CALENDAR_VIEW_OWN'
  FROM app_role r
 WHERE r.code = 'ADVISOR';

-- ---------------------------------------------------------------------------
-- Perfil de ruteo: estado persistente de la ronda y migración del horario simple
-- ---------------------------------------------------------------------------
ALTER TABLE crm_advisor_profile
    ADD COLUMN legacy_shift TEXT,
    ADD COLUMN calendar_needs_setup BOOLEAN NOT NULL DEFAULT FALSE,
    -- Ronda equitativa: a quien le tocó hace más tiempo va primero.
    ADD COLUMN round_last_at TIMESTAMPTZ,
    -- Ronda ponderada (smooth weighted round robin): peso acumulado.
    ADD COLUMN round_current_weight INTEGER NOT NULL DEFAULT 0;

UPDATE crm_advisor_profile
   SET legacy_shift = to_char(shift_start, 'HH24:MI') || '–' || to_char(shift_end, 'HH24:MI'),
       calendar_needs_setup = TRUE
 WHERE shift_start IS NOT NULL AND shift_end IS NOT NULL;

-- Quien recibía leads sin horario definido tampoco tiene calendario todavía.
UPDATE crm_advisor_profile SET calendar_needs_setup = TRUE WHERE receives_leads;

ALTER TABLE crm_advisor_profile
    DROP COLUMN shift_start,
    DROP COLUMN shift_end;

-- ---------------------------------------------------------------------------
-- Turnos (una fecha o repetición semanal) y fechas omitidas de una serie
-- ---------------------------------------------------------------------------
CREATE TABLE crm_shift (
    id                UUID PRIMARY KEY,
    advisor_user_id   UUID NOT NULL REFERENCES app_user (id),
    kind              VARCHAR(16) NOT NULL,           -- REGULAR | GUARD
    recurrence        VARCHAR(16) NOT NULL,           -- ONCE | WEEKLY
    -- Días ISO (1 = lunes … 7 = domingo) separados por coma; solo WEEKLY.
    days_of_week      VARCHAR(20),
    start_date        DATE NOT NULL,                  -- ONCE: la fecha; WEEKLY: desde
    end_date          DATE,                           -- WEEKLY: hasta (opcional)
    start_time        TIME NOT NULL,
    -- end_time <= start_time = cruza la medianoche (termina al día siguiente).
    end_time          TIME NOT NULL,
    assignable        BOOLEAN NOT NULL DEFAULT TRUE,  -- participa en la asignación automática
    notes             TEXT,
    -- Turno puntual que reemplaza una fecha de una serie ("solo este turno").
    origin_shift_id   UUID,
    created_by_id     UUID,
    created_by_name   TEXT,
    updated_by_name   TEXT,
    deleted_at        TIMESTAMPTZ,
    deleted_by_name   TEXT,
    version           BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    CHECK (start_time <> end_time),
    CHECK (end_date IS NULL OR end_date >= start_date),
    CHECK (recurrence <> 'WEEKLY' OR days_of_week IS NOT NULL)
);

CREATE INDEX idx_crm_shift_advisor ON crm_shift (advisor_user_id) WHERE deleted_at IS NULL;

CREATE TABLE crm_shift_skip (
    id               UUID PRIMARY KEY,
    shift_id         UUID NOT NULL REFERENCES crm_shift (id) ON DELETE CASCADE,
    occurrence_date  DATE NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    UNIQUE (shift_id, occurrence_date)
);

-- ---------------------------------------------------------------------------
-- Ausencias: prevalecen sobre cualquier turno
-- ---------------------------------------------------------------------------
CREATE TABLE crm_absence (
    id                UUID PRIMARY KEY,
    advisor_user_id   UUID NOT NULL REFERENCES app_user (id),
    kind              VARCHAR(16) NOT NULL,           -- VACATION | PERMISSION | REST | UNAVAILABLE
    starts_at         TIMESTAMPTZ NOT NULL,
    ends_at           TIMESTAMPTZ NOT NULL,
    notes             TEXT,
    created_by_name   TEXT,
    updated_by_name   TEXT,
    deleted_at        TIMESTAMPTZ,
    deleted_by_name   TEXT,
    version           BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    CHECK (ends_at > starts_at)
);

CREATE INDEX idx_crm_absence_advisor ON crm_absence (advisor_user_id, starts_at) WHERE deleted_at IS NULL;

-- Quién creó, modificó o eliminó turnos y ausencias.
CREATE TABLE crm_calendar_audit (
    id               UUID PRIMARY KEY,
    entity           VARCHAR(16) NOT NULL,            -- SHIFT | ABSENCE | SETTINGS
    entity_id        UUID,
    advisor_user_id  UUID,
    action           VARCHAR(16) NOT NULL,            -- CREATED | UPDATED | DELETED
    detail           TEXT NOT NULL,
    actor_user_id    UUID,
    actor_name       TEXT,
    created_at       TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_crm_calendar_audit_recent ON crm_calendar_audit (created_at DESC);

-- ---------------------------------------------------------------------------
-- Política de asignación (una sola fila)
-- ---------------------------------------------------------------------------
CREATE TABLE crm_assignment_settings (
    id                      SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    round_mode              VARCHAR(16) NOT NULL DEFAULT 'EQUAL',          -- EQUAL | WEIGHTED
    owner_off_shift_policy  VARCHAR(24) NOT NULL DEFAULT 'RESPECT_OWNER',  -- RESPECT_OWNER | ROUTE_TO_GUARD
    updated_by_name         TEXT,
    version                 BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL
);

INSERT INTO crm_assignment_settings (id, created_at, updated_at) VALUES (1, now(), now());

-- ---------------------------------------------------------------------------
-- Lead: estado de asignación (separado de la etapa comercial) y bitácora
-- ---------------------------------------------------------------------------
ALTER TABLE lead
    -- ASSIGNED | PENDING_OWNER (espera el turno del asesor de la propiedad) | PENDING_SCHEDULE (sin asesores en turno) | UNASSIGNED
    ADD COLUMN assignment_status VARCHAR(24) NOT NULL DEFAULT 'ASSIGNED',
    ADD COLUMN assignment_rule TEXT,
    ADD COLUMN assignment_shift_id UUID,
    ADD COLUMN pending_reason TEXT,
    ADD COLUMN pending_since TIMESTAMPTZ,
    ADD COLUMN next_shift_at TIMESTAMPTZ,
    -- Asesor responsable de la PROPIEDAD (21Online), distinto del que atiende el lead.
    ADD COLUMN property_advisor_user_id UUID,
    ADD COLUMN property_advisor_online21_id BIGINT,
    -- Cliente existente que pregunta por una propiedad de otro asesor: requiere coordinación.
    ADD COLUMN coordination_note TEXT;

-- Los leads que ya estaban sin asesor quedan "sin asignar" (no se reparten solos de golpe).
UPDATE lead SET assignment_status = 'UNASSIGNED' WHERE advisor_user_id IS NULL;

CREATE INDEX idx_lead_pending ON lead (assignment_status, pending_since) WHERE assignment_status IN ('PENDING_OWNER', 'PENDING_SCHEDULE');

CREATE TABLE lead_assignment_log (
    id                UUID PRIMARY KEY,
    lead_id           UUID NOT NULL REFERENCES lead (id) ON DELETE CASCADE,
    outcome           VARCHAR(24) NOT NULL,           -- ASSIGNED | PENDING | KEPT | CONFLICT | REASSIGNED
    advisor_user_id   UUID,
    reason            VARCHAR(24),                    -- PROPERTY_OWNER | CONTINUITY | ROULETTE | GUARD | MANUAL
    rule              TEXT NOT NULL,
    shift_id          UUID,
    round_mode        VARCHAR(16),
    detail            TEXT,
    actor_user_id     UUID,
    actor_name        TEXT,
    created_at        TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_lead_assignment_log_lead ON lead_assignment_log (lead_id, created_at DESC);
