-- Chat de consultas adicionales sobre una factibilidad: el asesor pregunta y la IA responde con
-- el reporte, los datos del inmueble y búsqueda web como contexto.
--
-- role: USER (pregunta del asesor) o ASSISTANT (respuesta de la IA).
-- status: PENDING (la IA trabaja en segundo plano), DONE o FAILED.

CREATE TABLE feasibility_chat_message (
    id               UUID PRIMARY KEY,
    report_id        UUID NOT NULL REFERENCES feasibility_report (id) ON DELETE CASCADE,
    role             VARCHAR(16) NOT NULL,
    status           VARCHAR(16) NOT NULL,
    content          TEXT,
    sources_json     TEXT,
    ai_response_id   VARCHAR(120),
    error            TEXT,
    author_user_id   UUID,
    author_name      VARCHAR(160),
    created_at       TIMESTAMPTZ NOT NULL,
    completed_at     TIMESTAMPTZ
);

CREATE INDEX idx_feasibility_chat_report ON feasibility_chat_message (report_id, created_at);
CREATE INDEX idx_feasibility_chat_pending ON feasibility_chat_message (status) WHERE status = 'PENDING';
