-- Avisos dentro del sistema para el personal (campana del encabezado):
-- recordatorios de actividades de leads (CC#30) y alertas de detractores de
-- la encuesta NPS (CC#28). link: ruta del panel a la que lleva el aviso.
CREATE TABLE staff_notification (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    kind        VARCHAR(40) NOT NULL,
    title       VARCHAR(200) NOT NULL,
    body        TEXT,
    link        VARCHAR(300),
    -- Evita repetir el mismo aviso (p. ej. "task-reminder:<id>").
    dedupe_key  VARCHAR(120),
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_staff_notification_user ON staff_notification (user_id, created_at DESC);
CREATE UNIQUE INDEX uq_staff_notification_dedupe ON staff_notification (user_id, dedupe_key) WHERE dedupe_key IS NOT NULL;
