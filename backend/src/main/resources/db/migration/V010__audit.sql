-- Módulo audit: rastro técnico separado de la línea de tiempo amigable de UI
-- (ver AGENTS §48-49). Ninguna de las dos tablas debe usarse para guardar PII.

CREATE TABLE audit_event (
    id             UUID PRIMARY KEY,
    occurred_at    TIMESTAMPTZ NOT NULL,
    event_type     VARCHAR(96) NOT NULL,
    aggregate_type VARCHAR(32) NOT NULL,
    aggregate_id   UUID NOT NULL,
    actor_user_id  UUID,
    summary        VARCHAR(512) NOT NULL
);

CREATE INDEX idx_audit_event_aggregate ON audit_event (aggregate_id, occurred_at);

CREATE TABLE activity (
    id            UUID PRIMARY KEY,
    expediente_id UUID NOT NULL REFERENCES expediente (id) ON DELETE CASCADE,
    occurred_at   TIMESTAMPTZ NOT NULL,
    category      VARCHAR(24) NOT NULL,
    message       VARCHAR(512) NOT NULL
);

CREATE INDEX idx_activity_expediente ON activity (expediente_id, occurred_at);
