-- Factibilidad comercial generada con IA a partir de los documentos del expediente.
--
-- Dos etapas, cada una una petición en segundo plano a la IA (con búsqueda web):
-- 1) el reporte de factibilidad (prompt "Analista de Factibilidad 3.0") y
-- 2) los datos de la presentación para el propietario (prompt "Presentación de
--    factibilidad"). Después el servidor arma el Word y el PowerPoint.
--
-- status: ANALYZING, PRESENTING, READY o FAILED. ai_response_id es la petición en
-- curso (se consulta cada pocos segundos hasta que la IA termina).

CREATE TABLE feasibility_report (
    id                    UUID PRIMARY KEY,
    expediente_id         UUID NOT NULL REFERENCES expediente (id) ON DELETE CASCADE,
    version_number        INT NOT NULL,
    status                VARCHAR(16) NOT NULL,
    requested_by          UUID NOT NULL REFERENCES app_user (id),
    requested_by_name     VARCHAR(160),
    requested_at          TIMESTAMPTZ NOT NULL,
    stage_started_at      TIMESTAMPTZ NOT NULL,
    completed_at          TIMESTAMPTZ,
    -- Lo que capturó el asesor (precio solicitado, notas) y el contexto que se envió a la IA.
    input_json            TEXT NOT NULL,
    -- Fotos del inmueble que subió el asesor (JSON: lista de storage keys).
    photo_keys            TEXT,
    -- Archivos subidos al proveedor de IA para esta factibilidad (se borran al terminar).
    ai_file_ids           TEXT,
    ai_response_id        VARCHAR(120),
    model                 VARCHAR(80),
    report_markdown       TEXT,
    -- Fuentes citadas por la búsqueda web (JSON: [{title, url}]).
    sources_json          TEXT,
    presentation_json     TEXT,
    docx_storage_key      TEXT,
    pptx_storage_key      TEXT,
    error                 TEXT,
    UNIQUE (expediente_id, version_number)
);

CREATE INDEX idx_feasibility_report_status ON feasibility_report (status);
