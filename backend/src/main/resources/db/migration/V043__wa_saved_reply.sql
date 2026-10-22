-- Respuestas guardadas para contestar más rápido desde Conversaciones.
CREATE TABLE wa_saved_reply (
    id         UUID         PRIMARY KEY,
    title      VARCHAR(80)  NOT NULL,
    text       TEXT         NOT NULL,
    created_by VARCHAR(200),
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);
