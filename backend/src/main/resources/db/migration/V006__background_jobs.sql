-- Cola de trabajos persistente para el pipeline documental asíncrono
-- (ver AGENTS §34-35). Sin Kafka/RabbitMQ por ahora; diseñado para poder
-- agregarlos después sin cambiar el dominio.
CREATE TABLE background_job (
    id               UUID PRIMARY KEY,
    type             VARCHAR(64) NOT NULL,
    payload          TEXT NOT NULL,
    status           VARCHAR(16) NOT NULL,
    attempts         INT NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ NOT NULL,
    locked_at        TIMESTAMPTZ,
    locked_by        VARCHAR(64),
    created_at       TIMESTAMPTZ NOT NULL,
    last_error       TEXT
);

CREATE INDEX idx_background_job_poll ON background_job (status, next_attempt_at);
