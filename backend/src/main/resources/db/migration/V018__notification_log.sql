-- Notificaciones reales con historial de entrega (retroalimentación 25/09,
-- hallazgo 12): cada aviso queda registrado con su estado y, si falla, con
-- el error de entrega.
CREATE TABLE notification_log (
    id             UUID PRIMARY KEY,
    expediente_id  UUID REFERENCES expediente (id) ON DELETE CASCADE,
    kind           VARCHAR(64) NOT NULL,
    recipient      TEXT,
    subject        TEXT NOT NULL,
    status         VARCHAR(16) NOT NULL,
    attempts       INT NOT NULL DEFAULT 0,
    last_error     TEXT,
    created_at     TIMESTAMPTZ NOT NULL,
    sent_at        TIMESTAMPTZ
);

CREATE INDEX idx_notification_log_expediente ON notification_log (expediente_id, created_at);
